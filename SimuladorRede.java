import java.util.*;
import java.io.*;

/**
 * Simulador de Rede de Filas com topologia GENERICA (T1) por eventos discretos.
 *
 * Carrega o modelo de um arquivo .yml
 * define varias filas (servidores, capacidade, chegada externa e atendimento) e
 * uma rede de roteamento probabilistico entre elas. A fracao de probabilidade
 * que faltar para somar 1.0 em cada fila representa a saida do sistema.

 * Uso:  java SimuladorRede.java <modelo.yml>
 *
 * Criterio de parada: consumo do orcamento de numeros aleatorios (rndnumbersPerSeed).
 */
public class SimuladorRede {

    static final long LCG_A = 1664525L, LCG_C = 1013904223L, LCG_M = 4294967296L;
    static long previous;
    static long count; // orcamento de aleatorios (criterio de parada)

    static double nextRandom() {
        previous = (LCG_A * previous + LCG_C) % LCG_M;
        count--;
        return (double) previous / (double) LCG_M;
    }
    static double entre(double min, double max) { return min + (max - min) * nextRandom(); }

    static class Rota {
        final String destino; final double prob;
        Rota(String destino, double prob) { this.destino = destino; this.prob = prob; }
    }

    static class Fila {
        String nome;
        int servers;
        Integer capacity;              
        Double minArrival, maxArrival; 
        double minService, maxService;
        int customers = 0;
        long loss = 0;
        ArrayList<Double> times = new ArrayList<>(); // tempo acumulado por estado
        List<Rota> rotas = new ArrayList<>();

        boolean temChegadaExterna() { return minArrival != null && maxArrival != null; }
        boolean temEspaco() { return capacity == null || customers < capacity; }

        void acumula(double delta) {
            while (times.size() <= customers) times.add(0.0);
            times.set(customers, times.get(customers) + delta);
        }
    }

    static Map<String, Fila> filas = new LinkedHashMap<>();
    static double tempoGlobal = 0.0;

    // Escalonador
    static final int CHEGADA = 0, SAIDA = 1;
    static class Evento implements Comparable<Evento> {
        final double tempo; final int tipo; final Fila fila;
        Evento(double tempo, int tipo, Fila fila) { this.tempo = tempo; this.tipo = tipo; this.fila = fila; }
        public int compareTo(Evento o) { return Double.compare(this.tempo, o.tempo); }
    }
    static PriorityQueue<Evento> escalonador = new PriorityQueue<>();

    // acumula o tempo do estado atual em todas as filas e avanca o relogio
    static void acumulaTempo(double novoTempo) {
        double delta = novoTempo - tempoGlobal;
        for (Fila f : filas.values()) f.acumula(delta);
        tempoGlobal = novoTempo;
    }

    static void agendaChegada(Fila f) {
        escalonador.add(new Evento(tempoGlobal + entre(f.minArrival, f.maxArrival), CHEGADA, f));
    }
    static void agendaSaida(Fila f) {
        escalonador.add(new Evento(tempoGlobal + entre(f.minService, f.maxService), SAIDA, f));
    }

    static void entra(Fila f) {
        if (f.temEspaco()) {
            f.customers++;
            if (f.customers <= f.servers) agendaSaida(f); // ha servidor livre
        } else {
            f.loss++;                                      // fila cheia -> perda
        }
    }

    // sorteia para onde vai o cliente que terminou o atendimento 
    static Fila sorteiaDestino(Fila f) {
        if (f.rotas.isEmpty()) return null; // sem rotas -> sai do sistema 
        double u = nextRandom();
        double cum = 0.0;
        for (Rota r : f.rotas) {
            cum += r.prob;
            if (u < cum) return filas.get(r.destino);
        }
        return null;
    }

    static void chegada(Evento e) {
        acumulaTempo(e.tempo);
        entra(e.fila);
        agendaChegada(e.fila); // agenda a proxima chegada externa
    }

    static void saida(Evento e) {
        acumulaTempo(e.tempo);
        Fila f = e.fila;
        f.customers--;
        if (f.customers >= f.servers) agendaSaida(f); // proximo da espera ocupa o servidor
        Fila destino = sorteiaDestino(f);
        if (destino != null) entra(destino);          // roteamento senao, sai do sistema
    }

    static void simular(long orcamentoAleatorios, long semente, Map<String, Double> primeirasChegadas) {
        previous = semente;
        count = orcamentoAleatorios;
        tempoGlobal = 0.0;
        // agenda as primeiras chegadas externas
        for (Map.Entry<String, Double> e : primeirasChegadas.entrySet()) {
            escalonador.add(new Evento(e.getValue(), CHEGADA, filas.get(e.getKey())));
        }
        while (count > 0 && !escalonador.isEmpty()) {
            Evento e = escalonador.poll();
            if (e.tipo == CHEGADA) chegada(e);
            else                   saida(e);
        }
    }

    static void relatorio() {
        System.out.println("=========================================================");
        System.out.println("======================   RELATORIO   ====================");
        System.out.println("=========================================================");
        for (Fila f : filas.values()) {
            String cls = "G/G/" + f.servers + (f.capacity == null ? "" : "/" + f.capacity);
            System.out.println("*********************************************************");
            System.out.println("Fila:   " + f.nome + " (" + cls + ")");
            if (f.temChegadaExterna())
                System.out.printf(Locale.US, "Chegada:  %.1f ... %.1f%n", f.minArrival, f.maxArrival);
            System.out.printf(Locale.US, "Atendimento: %.1f ... %.1f%n", f.minService, f.maxService);
            System.out.println("*********************************************************");
            System.out.println("   Estado            Tempo              Probabilidade");
            int maxEstado = (f.capacity != null) ? f.capacity : f.times.size() - 1;
            for (int i = 0; i <= maxEstado; i++) {
                double t = (i < f.times.size()) ? f.times.get(i) : 0.0;
                double prob = (tempoGlobal > 0) ? t / tempoGlobal * 100.0 : 0.0;
                System.out.printf(Locale.US, "   %3d       %15.4f            %7.2f%%%n", i, t, prob);
            }
            System.out.println();
            System.out.println("Clientes perdidos: " + f.loss);
            System.out.println();
        }
        System.out.println("=========================================================");
        System.out.printf(Locale.US, "Tempo global de simulacao: %.4f%n", tempoGlobal);
        System.out.println("=========================================================");
    }

    static long globalRandoms = 100_000;
    static long semente = 1;
    static Map<String, Double> primeirasChegadas = new LinkedHashMap<>();

    static final Set<String> PROPS = new HashSet<>(Arrays.asList(
            "servers", "capacity", "minArrival", "maxArrival", "minService", "maxService"));

    static void carregarModelo(String caminho) throws IOException {
        String secao = "";
        Fila filaAtual = null;
        String rSource = null, rTarget = null; Double rProb = null;

        try (BufferedReader br = new BufferedReader(new FileReader(caminho))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                String s = linha.trim();
                if (s.isEmpty() || s.startsWith("#") || s.startsWith("!")) continue;

                // troca de secao
                if (s.equals("arrivals:")) { secao = "arrivals"; continue; }
                if (s.equals("queues:"))   { secao = "queues";   continue; }
                if (s.equals("network:"))  { secao = "network";  continue; }
                if (s.equals("seeds:"))    { secao = "seeds";     continue; }
                if (s.startsWith("rndnumbersPerSeed:")) {
                    globalRandoms = Long.parseLong(s.substring(s.indexOf(':') + 1).trim());
                    secao = ""; continue;
                }

                if (secao.equals("arrivals")) {
                    String[] kv = s.split(":", 2);
                    primeirasChegadas.put(kv[0].trim(), Double.parseDouble(kv[1].trim()));

                } else if (secao.equals("queues")) {
                    String[] kv = s.split(":", 2);
                    String chave = kv[0].trim();
                    String valor = kv.length > 1 ? kv[1].trim() : "";
                    if (valor.isEmpty() && !PROPS.contains(chave)) {
                        // nome de uma nova fila
                        filaAtual = new Fila();
                        filaAtual.nome = chave;
                        filas.put(chave, filaAtual);
                    } else if (filaAtual != null) {
                        switch (chave) {
                            case "servers":    filaAtual.servers    = Integer.parseInt(valor); break;
                            case "capacity":   filaAtual.capacity   = Integer.parseInt(valor); break;
                            case "minArrival": filaAtual.minArrival = Double.parseDouble(valor); break;
                            case "maxArrival": filaAtual.maxArrival = Double.parseDouble(valor); break;
                            case "minService": filaAtual.minService = Double.parseDouble(valor); break;
                            case "maxService": filaAtual.maxService = Double.parseDouble(valor); break;
                        }
                    }

                } else if (secao.equals("network")) {
                    boolean novoItem = s.startsWith("-");
                    if (novoItem) {
                        // fecha o item anterior, se completo
                        if (rSource != null && rTarget != null && rProb != null)
                            filas.get(rSource).rotas.add(new Rota(rTarget, rProb));
                        rSource = null; rTarget = null; rProb = null;
                        s = s.substring(1).trim(); // remove o "-"
                    }
                    String[] kv = s.split(":", 2);
                    String chave = kv[0].trim();
                    String valor = kv.length > 1 ? kv[1].trim() : "";
                    if (chave.equals("source"))      rSource = valor;
                    else if (chave.equals("target")) rTarget = valor;
                    else if (chave.equals("probability")) rProb = Double.parseDouble(valor);

                } else if (secao.equals("seeds")) {
                    String v = s.startsWith("-") ? s.substring(1).trim() : s;
                    if (!v.isEmpty()) { semente = Long.parseLong(v); secao = ""; } // usa a 1a semente
                }
            }
            // fecha o ultimo item de rede
            if (rSource != null && rTarget != null && rProb != null)
                filas.get(rSource).rotas.add(new Rota(rTarget, rProb));
        }
    }

    public static void main(String[] args) throws IOException {
        String caminho = (args.length > 0) ? args[0] : "modelo.yml";
        carregarModelo(caminho);
        System.out.println("Modelo carregado de: " + caminho);
        System.out.println("Filas: " + filas.keySet() + " | aleatorios: " + globalRandoms + " | semente: " + semente);
        System.out.println();
        simular(globalRandoms, semente, primeirasChegadas);
        relatorio();
    }
}
