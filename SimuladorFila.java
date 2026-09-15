import java.util.PriorityQueue;

/**
 * Simulador de Fila Simples (G/G/c/K) por eventos discretos.
 *
 * Estrutura proposital simples: variaveis "globais" (campos estaticos) e
 * funcoes auxiliares diretas, conforme sugerido no enunciado do modulo.
 *
 * Etapas implementadas:
 *   1) Gerador de numeros pseudoaleatorios pelo Metodo Congruente Linear (LCG)
 *      e a funcao nextRandom() normalizada em [0,1).
 *   2) Escalonador de eventos (chegada / saida) via fila de prioridade por tempo.
 *   3) Laco principal na main() com criterio de parada por consumo de aleatorios.
 *   4) Distribuicao de probabilidade dos estados + tempos acumulados + perdas.
 *
 * Parametros das filas (reentrega M4):
 *   1) G/G/1/5  chegadas 3..5  atendimento 4..5
 *   2) G/G/2/5  chegadas 3..5  atendimento 4..5
 *   Primeiro cliente chega no tempo 3.0 nos dois casos.
 */
public class SimuladorFila {

    // ----------------- Parametros do gerador LCG (Numerical Recipes) ----------
    // previous = (a * previous + c) % M   ->   full period (Hull-Dobell)
    static final long LCG_A = 1664525L;
    static final long LCG_C = 1013904223L;
    static final long LCG_M = 4294967296L; // 2^32
    static final long SEED  = 12345L;      // semente inicial da sequencia
    static long previous;                  // ultimo valor gerado da sequencia

    // ----------------- Variaveis "globais" da simulacao -----------------------
    static long count;          // orcamento de numeros aleatorios (criterio de parada)
    static double tempoGlobal;  // relogio da simulacao
    static int status;          // numero de clientes no sistema (estado)
    static double[] tempos;     // tempo acumulado em cada estado [0..K]
    static long perdas;         // clientes perdidos (fila cheia)

    // ----------------- Configuracao da fila em simulacao ----------------------
    static int servidores;      // c
    static int capacidade;      // K (capacidade total do sistema)
    static double chegadaMin, chegadaMax;         // intervalo entre chegadas
    static double atendimentoMin, atendimentoMax; // tempo de atendimento

    // ----------------- Escalonador --------------------------------------------
    static final int CHEGADA = 0;
    static final int SAIDA   = 1;

    static class Evento {
        final double tempo;
        final int tipo;
        Evento(double tempo, int tipo) { this.tempo = tempo; this.tipo = tipo; }
    }

    // Escalonador: sempre retira o evento de MENOR tempo agendado.
    static PriorityQueue<Evento> escalonador;

    // ============================ Gerador =====================================
    /** Gera um pseudoaleatorio normalizado em [0,1) e consome 1 do orcamento. */
    static double nextRandom() {
        previous = (LCG_A * previous + LCG_C) % LCG_M;
        count--; // consumimos um numero aleatorio
        return (double) previous / (double) LCG_M;
    }

    /** Sorteia um valor uniforme no intervalo [min, max]. */
    static double aleatorioEntre(double min, double max) {
        return min + (max - min) * nextRandom();
    }

    // ========================= Agendamento ====================================
    static void agendaChegada() {
        double t = tempoGlobal + aleatorioEntre(chegadaMin, chegadaMax);
        escalonador.add(new Evento(t, CHEGADA));
    }

    static void agendaSaida() {
        double t = tempoGlobal + aleatorioEntre(atendimentoMin, atendimentoMax);
        escalonador.add(new Evento(t, SAIDA));
    }

    // ====================== Tratamento de eventos =============================
    static void chegada(Evento e) {
        // acumula o tempo em que o sistema permaneceu no estado atual
        tempos[status] += e.tempo - tempoGlobal;
        tempoGlobal = e.tempo;

        if (status < capacidade) {
            status++;
            if (status <= servidores) {
                agendaSaida(); // ha servidor livre -> cliente entra em atendimento
            }
        } else {
            perdas++; // sistema cheio -> cliente perdido
        }
        agendaChegada(); // agenda a proxima chegada
    }

    static void saida(Evento e) {
        tempos[status] += e.tempo - tempoGlobal;
        tempoGlobal = e.tempo;

        status--;
        if (status >= servidores) {
            agendaSaida(); // ha cliente esperando -> ocupa o servidor liberado
        }
    }

    // ========================== Simulacao =====================================
    static void simular(String nome,
                        int servidores, int capacidade,
                        double chegadaMin, double chegadaMax,
                        double atendimentoMin, double atendimentoMax,
                        double primeiraChegada, long orcamentoAleatorios) {

        // inicializa ou reinicia o gerador e as variaveis globais
        previous = SEED;
        count = orcamentoAleatorios;
        tempoGlobal = 0.0;
        status = 0;
        perdas = 0;
        SimuladorFila.servidores = servidores;
        SimuladorFila.capacidade = capacidade;
        SimuladorFila.chegadaMin = chegadaMin;
        SimuladorFila.chegadaMax = chegadaMax;
        SimuladorFila.atendimentoMin = atendimentoMin;
        SimuladorFila.atendimentoMax = atendimentoMax;
        tempos = new double[capacidade + 1];
        escalonador = new PriorityQueue<>((x, y) -> Double.compare(x.tempo, y.tempo));

        // primeiro cliente chega no tempo dado (NAO consome aleatorio)
        escalonador.add(new Evento(primeiraChegada, CHEGADA));

        // laco principal: executa ate esgotar o orcamento de aleatorios
        while (count > 0 && !escalonador.isEmpty()) {
            Evento e = escalonador.poll();
            if (e.tipo == CHEGADA) chegada(e);
            else                   saida(e);
        }

        imprimirResultado(nome);
    }

    static void imprimirResultado(String nome) {
        System.out.println("==========================================================");
        System.out.println("Fila: " + nome);
        System.out.printf ("Servidores=%d  Capacidade=%d  Chegada[%.1f..%.1f]  Atendimento[%.1f..%.1f]%n",
                servidores, capacidade, chegadaMin, chegadaMax, atendimentoMin, atendimentoMax);
        System.out.println("----------------------------------------------------------");
        System.out.println("Estado |     Tempo acumulado |   Probabilidade");
        for (int i = 0; i <= capacidade; i++) {
            double prob = tempos[i] / tempoGlobal;
            System.out.printf("  %2d   | %18.4f  |  %8.4f %%%n", i, tempos[i], prob * 100.0);
        }
        System.out.println("----------------------------------------------------------");
        System.out.printf ("Tempo global de simulacao : %.4f%n", tempoGlobal);
        System.out.printf ("Clientes perdidos         : %d%n", perdas);
        System.out.println();
    }

    public static void main(String[] args) {
        final long ALEATORIOS = 100_000;
        final double PRIMEIRA_CHEGADA = 3.0;

        // 1) G/G/1/5  chegadas 3..5  atendimento 4..5
        simular("G/G/1/5 (chegada 3..5, atend. 4..5)", 1, 5, 3, 5, 4, 5, PRIMEIRA_CHEGADA, ALEATORIOS);

        // 2) G/G/2/5  chegadas 3..5  atendimento 4..5
        simular("G/G/2/5 (chegada 3..5, atend. 4..5)", 2, 5, 3, 5, 4, 5, PRIMEIRA_CHEGADA, ALEATORIOS);
    }
}