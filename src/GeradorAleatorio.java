import java.util.HashSet;
import java.util.Random;
import java.util.Set;

// gerador de grafo aleatório simples (sem laços e sem arestas paralelas),
// ponderado (peso inteiro de 1 a 100) e rotulado ("e1", "e2", ...).
// entradas: nº de nós, nº de arestas e se deve ser conexo ou não.
// - conexo: monta uma árvore geradora (n-1 arestas, liga todo mundo) e completa com arestas extras;
//   no grafo direcionado a árvore garante conexidade fraca.
// - desconexo: divide os vértices em 2 grupos e só cria arestas dentro de cada grupo (>= 2 componentes).
public final class GeradorAleatorio {

    private static final int PESO_MAXIMO = 100;

    private GeradorAleatorio() {
    }

    // atalho: não direcionado, semente aleatória
    public static Grafo gerar(int numNos, int numArestas, boolean conexo) {
        return gerar(numNos, numArestas, conexo, false, new Random());
    }

    // atalho: escolhe se é direcionado, semente aleatória
    public static Grafo gerar(int numNos, int numArestas, boolean conexo, boolean direcionado) {
        return gerar(numNos, numArestas, conexo, direcionado, new Random());
    }

    // versão principal; as outras duas só chamam esta com uma semente nova
    public static Grafo gerar(int numNos, int numArestas, boolean conexo, boolean direcionado, Random rnd) {
        if (numNos < 1) {
            throw new IllegalArgumentException("O número de nós deve ser pelo menos 1.");
        }
        if (numArestas < 0) {
            throw new IllegalArgumentException("O número de arestas não pode ser negativo.");
        }
        // maxArestas = nº máximo de arestas sem repetir (grafo completo)
        if (numArestas > maxArestas(numNos, direcionado)) {
            throw new IllegalArgumentException("Com " + numNos + " nós cabem no máximo "
                    + maxArestas(numNos, direcionado) + " arestas (grafo simples).");
        }

        // grafo com todos os vértices e nenhuma aresta
        Grafo g = new Grafo(numNos, direcionado);
        // conjunto de arestas que já existem (não deixa repetir)
        Set<Long> usadas = new HashSet<>();


        if (conexo) {
            if (numArestas < numNos - 1) {
                throw new IllegalArgumentException("Um grafo conexo com " + numNos
                        + " nós precisa de pelo menos " + (numNos - 1) + " arestas.");
            }
            // vértices numa ordem aleatória
            int[] ordem = permutacao(numNos, rnd);
            // monta a árvore geradora (liga todos os vértices)
            for (int i = 1; i < numNos; i++) {
                int a = ordem[i];
                int b = ordem[rnd.nextInt(i)];
                if (direcionado && rnd.nextBoolean()) {
                    int t = a;
                    a = b;
                    b = t;
                }
                inserir(g, a, b, usadas, rnd);
            }
            // a árvore tem n-1 arestas; completar sorteia o resto até numArestas
            completar(g, numArestas, usadas, rnd);

        } else {
            // desconexo: separa os vértices em 2 grupos (A e B) e só cria arestas dentro de cada grupo
            if (numNos < 2) {
                throw new IllegalArgumentException("Um grafo desconexo precisa de pelo menos 2 nós.");
            }

            // quantos vértices no grupo A
            int k = escolherCorte(numNos, numArestas, direcionado, rnd);
            // capacidade (máx. de arestas) de cada grupo
            long capA = maxArestas(k, direcionado);
            long capB = maxArestas(numNos - k, direcionado);

            // divide as arestas proporcionalmente à capacidade de cada grupo
            long noA = Math.round((double) numArestas * capA / (capA + capB));
            noA = Math.min(noA, capA);
            long noB = numArestas - noA;
            if (noB > capB) {
                noA += noB - capB;
                noB = capB;
            }

            // embaralha e divide os vértices: grupo A = posições [0, k); grupo B = posições [k, numNos)
            int[] ordem = permutacao(numNos, rnd);
            completarGrupo(g, (int) noA, ordem, 0, k, usadas, rnd);
            completarGrupo(g, (int) noB, ordem, k, numNos, usadas, rnd);
        }
        return g;
    }

    public static long maxArestas(long n, boolean direcionado) {
        long pares = n * (n - 1);
        return direcionado ? pares : pares / 2;
    }

    // ---- auxiliares

    // decide quantos vértices vão para o grupo A (o resto vai para B)
    private static int escolherCorte(int n, int m, boolean direcionado, Random rnd) {
        // sorteia um tamanho inicial
        int k = 1 + rnd.nextInt(n / 2);
        // se as arestas não couberem nos 2 grupos, diminui k
        while (k > 1 && maxArestas(k, direcionado) + maxArestas(n - k, direcionado) < m) {
            k--;
        }
        // se nem no melhor corte couber, é mais arestas do que um grafo desconexo comporta
        if (maxArestas(k, direcionado) + maxArestas(n - k, direcionado) < m) {
            throw new IllegalArgumentException("Com " + n + " nós, um grafo desconexo comporta no máximo "
                    + (maxArestas(1, direcionado) + maxArestas(n - 1, direcionado)) + " arestas.");
        }
        return k;
    }

    // conexo: completa usando todos os vértices
    private static void completar(Grafo g, int alvo, Set<Long> usadas, Random rnd) {
        int[] ids = new int[g.getNumVertices()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i;
        }
        adicionarAleatorias(g, alvo - g.getNumArestas(), ids, usadas, rnd);
    }

    // desconexo: completa usando só o pedaço [ini, fim) da ordem embaralhada
    private static void completarGrupo(Grafo g, int quantidade, int[] ordem, int ini, int fim,
                                       Set<Long> usadas, Random rnd) {
        int[] ids = new int[fim - ini];
        // copia só o pedaço do grupo para uma lista nova
        System.arraycopy(ordem, ini, ids, 0, ids.length);
        adicionarAleatorias(g, quantidade, ids, usadas, rnd);
    }

    // cria "quantidade" arestas novas usando só os vértices da lista ids
    private static void adicionarAleatorias(Grafo g, int quantidade, int[] ids, Set<Long> usadas, Random rnd) {
        if (quantidade <= 0) {
            return;
        }
        int k = ids.length;
        boolean dir = g.isDirecionado();
        long capacidade = maxArestas(k, dir);
        // conta quantas arestas o grupo já tem
        long jaUsadas = 0;
        for (int a : ids) {
            jaUsadas += g.getGrauSaida(a);
        }
        if (!dir) {
            jaUsadas /= 2;
        }
        // grupo vai passar da metade da capacidade?
        if (jaUsadas + quantidade > capacidade / 2) {
            // modo denso (grupo quase cheio): lista todos os pares de vértices ainda livres e
            // sorteia entre eles sem repetir. cada par livre fica em dois vetores paralelos:
            // pontaA[p] e pontaB[p] são as duas pontas da aresta candidata p.
            int livres = (int) (capacidade - jaUsadas);
            int[] pontaA = new int[livres];
            int[] pontaB = new int[livres];
            int p = 0;
            for (int i = 0; i < k; i++) {
                // não direcionado: só j > i (cada par aparece uma vez); direcionado: todo j != i
                for (int j = dir ? 0 : i + 1; j < k; j++) {
                    if (i == j) {
                        continue;
                    }
                    if (!usadas.contains(chave(ids[i], ids[j], dir, g.getNumVertices()))) {
                        pontaA[p] = ids[i];
                        pontaB[p] = ids[j];
                        p++;
                    }
                }
            }
            // embaralhamento parcial: troca o par i por um sorteado à frente e usa o primeiro
            for (int i = 0; i < quantidade; i++) {
                int r = i + rnd.nextInt(p - i);
                int ta = pontaA[i]; pontaA[i] = pontaA[r]; pontaA[r] = ta;
                int tb = pontaB[i]; pontaB[i] = pontaB[r]; pontaB[r] = tb;
                inserir(g, pontaA[i], pontaB[i], usadas, rnd);
            }
        } else {
            // modo rejeição (grupo com espaço): sorteia 2 vértices e tenta inserir; se já existe, sorteia de novo
            int adicionadas = 0;
            while (adicionadas < quantidade) {
                int a = ids[rnd.nextInt(k)];
                int b = ids[rnd.nextInt(k)];
                if (a != b && inserir(g, a, b, usadas, rnd)) {
                    adicionadas++;
                }
            }
        }
    }

    private static boolean inserir(Grafo g, int a, int b, Set<Long> usadas, Random rnd) {
        if (!usadas.add(chave(a, b, g.isDirecionado(), g.getNumVertices()))) {
            return false;
        }
        int peso = 1 + rnd.nextInt(PESO_MAXIMO);
        g.adicionarAresta(a, b, peso, "e" + (g.getNumArestas() + 1));
        return true;
    }

    // transforma o par (a, b) num único número para guardar no conjunto "usadas"
    // (não direcionado: ordena a,b para (1,2) e (2,1) virarem a mesma chave)
    private static long chave(int a, int b, boolean direcionado, int n) {
        if (!direcionado && a > b) {
            int t = a;
            a = b;
            b = t;
        }
        return (long) a * n + b;
    }

    // Fisher-Yates: devolve 0..n-1 numa ordem aleatória
    private static int[] permutacao(int n, Random rnd) {
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = v[i];
            v[i] = v[j];
            v[j] = t;
        }
        return v;
    }
}
