import java.util.PriorityQueue;
import java.util.Locale;

/**
 * Simulador de Rede de Filas em Tandem (M6) por eventos discretos.
 *
 * Rede validada (em linha / tandem):
 *   Fila 1 - G/G/2/3, chegadas 1..5, atendimento 4..5   (chegada externa)
 *   Fila 2 - G/G/1/5,               atendimento 1..3    (recebe 100% da Fila 1)
 *   Clientes chegam do exterior na Fila 1, passam para a Fila 2 e vao embora.
 *
 * Filas iniciam vazias, primeiro cliente chega em 2.5, parada em 100.000 aleatorios.
 */
public class Simuladortandem {

    static final long LCG_A = 1664525L;
    static final long LCG_C = 1013904223L;
    static final long LCG_M = 4294967296L; 
    static final long SEED  = 12345L;
    static long previous;
    static long count; // orcamento de aleatorios criterio de parada

    static double nextRandom() {
        previous = (LCG_A * previous + LCG_C) % LCG_M;
        count--;
        return (double) previous / (double) LCG_M;
    }
    static double aleatorioEntre(double min, double max) {
        return min + (max - min) * nextRandom();
    }

    // ============================ Classe Fila =================================
    static class Fila {
        final String nome;
        final int servers;      // c
        final int capacity;     // K
        final double minArrival, maxArrival;   // chegada externa se tiver
        final double minService, maxService;   // atendimento
        final boolean chegadaExterna;
        int customers = 0;      // clientes no sistema (estado)
        int loss = 0;           // perdas
        final double[] times;   // tempo acumulado por estado [0..K]

        // fila com chegada externa
        Fila(String nome, int servers, int capacity,
             double minArrival, double maxArrival,
             double minService, double maxService) {
            this.nome = nome;
            this.servers = servers; this.capacity = capacity;
            this.minArrival = minArrival; this.maxArrival = maxArrival;
            this.minService = minService; this.maxService = maxService;
            this.chegadaExterna = true;
            this.times = new double[capacity + 1];
        }
        // fila sem chegada externa (so recebe passagem de outra fila)
        Fila(String nome, int servers, int capacity,
             double minService, double maxService) {
            this.nome = nome;
            this.servers = servers; this.capacity = capacity;
            this.minArrival = 0; this.maxArrival = 0;
            this.minService = minService; this.maxService = maxService;
            this.chegadaExterna = false;
            this.times = new double[capacity + 1];
        }
        int status() { return customers; }
        void in()    { customers++; }
        void out()   { customers--; }
        void perda() { loss++; }
        double sorteioChegada()   { return aleatorioEntre(minArrival, maxArrival); }
        double sorteioAtendimento(){ return aleatorioEntre(minService, maxService); }
    }

    // ============================ Classe Evento ===============================
    static final int CHEGADA = 0, PASSAGEM = 1, SAIDA = 2;
    static class Evento implements Comparable<Evento> {
        final double tempo;
        final int tipo;
        Evento(double tempo, int tipo) { this.tempo = tempo; this.tipo = tipo; }
        @Override public int compareTo(Evento o) { return Double.compare(this.tempo, o.tempo); }
    }

    // ============================ Estado global ===============================
    static PriorityQueue<Evento> escalonador = new PriorityQueue<>();
    static double tempoGlobal = 0.0;
    static Fila fila1, fila2;

    // acumula o tempo do estado atual em AMBAS as filas e avanca o relogio
    static void acumulaTempo(double novoTempo) {
        double delta = novoTempo - tempoGlobal;
        fila1.times[fila1.status()] += delta;
        fila2.times[fila2.status()] += delta;
        tempoGlobal = novoTempo;
    }

    // ============================ Agendamentos ================================
    static void agendaChegada() {
        escalonador.add(new Evento(tempoGlobal + fila1.sorteioChegada(), CHEGADA));
    }
    static void agendaPassagem() { // termino de atendimento na Fila 1
        escalonador.add(new Evento(tempoGlobal + fila1.sorteioAtendimento(), PASSAGEM));
    }
    static void agendaSaida() {    // termino de atendimento na Fila 2
        escalonador.add(new Evento(tempoGlobal + fila2.sorteioAtendimento(), SAIDA));
    }

    // ======================== Tratamento de eventos ==========================
    static void chegada(Evento e) {          // chegada externa na Fila 1
        acumulaTempo(e.tempo);
        if (fila1.status() < fila1.capacity) {
            fila1.in();
            if (fila1.status() <= fila1.servers) agendaPassagem(); // servidor livre
        } else {
            fila1.perda();
        }
        agendaChegada();
    }

    static void passagem(Evento e) {         // sai da Fila 1 e entra na Fila 2
        acumulaTempo(e.tempo);
        // --- saida da Fila 1 ---
        fila1.out();
        if (fila1.status() >= fila1.servers) agendaPassagem(); // havia cliente esperando
        // --- entrada na Fila 2 ---
        if (fila2.status() < fila2.capacity) {
            fila2.in();
            if (fila2.status() <= fila2.servers) agendaSaida(); // servidor livre
        } else {
            fila2.perda();
        }
    }

    static void saida(Evento e) {            // sai da Fila 2 (deixa o sistema)
        acumulaTempo(e.tempo);
        fila2.out();
        if (fila2.status() >= fila2.servers) agendaSaida(); // havia cliente esperando
    }

    static void imprimir(Fila f) {
        System.out.println("==========================================================");
        System.out.println("Fila: " + f.nome);
        System.out.printf(Locale.US, "Servidores=%d  Capacidade=%d%n", f.servers, f.capacity);
        System.out.println("----------------------------------------------------------");
        System.out.println("Estado |     Tempo acumulado |   Probabilidade");
        for (int i = 0; i <= f.capacity; i++) {
            double prob = f.times[i] / tempoGlobal;
            System.out.printf(Locale.US, "  %2d   | %18.4f  |  %8.4f %%%n", i, f.times[i], prob * 100.0);
        }
        System.out.println("----------------------------------------------------------");
        System.out.printf(Locale.US, "Clientes perdidos : %d%n%n", f.loss);
    }

    public static void main(String[] args) {
        previous = SEED;
        count = 100_000;
        tempoGlobal = 0.0;

        // Fila 1 - G/G/2/3, chegadas 1..5, atendimento 4..5
        fila1 = new Fila("Fila 1 - G/G/2/3 (chegada 1..5, atend. 4..5)", 2, 3, 1, 5, 4, 5);
        // Fila 2 - G/G/1/5, atendimento 1..3 (sem chegada externa)
        fila2 = new Fila("Fila 2 - G/G/1/5 (sem chegada externa, atend. 1..3)", 1, 5, 1, 3);

        // primeiro cliente chega no tempo 2.5 (NAO consome aleatorio)
        escalonador.add(new Evento(2.5, CHEGADA));

        while (count > 0 && !escalonador.isEmpty()) {
            Evento e = escalonador.poll();
            switch (e.tipo) {
                case CHEGADA:  chegada(e);  break;
                case PASSAGEM: passagem(e); break;
                case SAIDA:    saida(e);    break;
            }
        }

        imprimir(fila1);
        imprimir(fila2);
        System.out.printf(Locale.US, "Tempo global de simulacao : %.4f%n", tempoGlobal);
    }
}