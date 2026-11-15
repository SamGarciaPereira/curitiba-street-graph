import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Gerador de grafos aleatórios simples (sem laços e sem arestas paralelas),
 * ponderados (peso inteiro de 1 a 100) e rotulados ("e1", "e2", ...).
 *
 * Entradas: número de nós, número de arestas e se o grafo deve ser conexo ou não.
 * - Conexo: primeiro sorteia uma árvore geradora aleatória (n-1 arestas) e depois
 *   completa com arestas aleatórias. Para grafo direcionado, a árvore garante
 *   conexidade fraca.
 * - Desconexo: divide os vértices em dois grupos e só cria arestas dentro de cada grupo,
 *   garantindo pelo menos dois componentes.
 */

// Recebe um número de nós, número de arestas e se o grafo deve ser conexo ou não e devolve um grafo aleatório.
// O grafo é gerado sem laõs e sem arestas repetidas.
// Cada aresta recebe um peso aleatório de 1 a 100 e um rótulo "e1", "e2" ...
// Conexo : primeiro monta uma árvore que passa por todos os vértices, o que jpa garante que tudo está ligado, depois sorteia
// arestas extras até completar o total.
// Desconexo : divide os vértices em 2 grupos e só sorteia as arestas dentro de cada grupo. Como não existe nenhuma aresta
// entre os grupos, o grafo fica com pelo menos 2 pedaõs


public final class GeradorAleatorio {

    private static final int PESO_MAXIMO = 100;

    private GeradorAleatorio() {
    }

    // Direcionado
    public static Grafo gerar(int numNos, int numArestas, boolean conexo) {
        return gerar(numNos, numArestas, conexo, false, new Random());
    }

    // Escolhe
    public static Grafo gerar(int numNos, int numArestas, boolean conexo, boolean direcionado) {
        return gerar(numNos, numArestas, conexo, direcionado, new Random());
    }

    // A que realmente faz o trabalho , as duas primeiras só chamam essa terceira.
    public static Grafo gerar(int numNos, int numArestas, boolean conexo, boolean direcionado, Random rnd) {
        if (numNos < 1) {
            throw new IllegalArgumentException("O número de nós deve ser pelo menos 1.");
        }
        if (numArestas < 0) {
            throw new IllegalArgumentException("O número de arestas não pode ser negativo.");
        }
        // maxarestas é a fórmula do grafo completo, o número máximo de arestas que cabe sem repetir
        if (numArestas > maxArestas(numNos, direcionado)) {
            throw new IllegalArgumentException("Com " + numNos + " nós cabem no máximo "
                    + maxArestas(numNos, direcionado) + " arestas (grafo simples).");
        }

        // grafo com todos os vértices sem nenhuma aresta
        Grafo g = new Grafo(numNos, direcionado);
        // conjunto de arestas que já existem (não deixa repetir)
        Set<Long> usadas = new HashSet<>();


        if (conexo) {
            if (numArestas < numNos - 1) {
                throw new IllegalArgumentException("Um grafo conexo com " + numNos
                        + " nós precisa de pelo menos " + (numNos - 1) + " arestas.");
            }
            // Cria um vetor com todos os vértices numa ordem aleatória
            int[] ordem = permutacao(numNos, rnd);
            // Monta a árvore
            // Vou enviar um print no grupo da execução pra ficar mais fácil de entender
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
            // A arvore tem n-1 arestas, o completar vai sorteear o resto até chegar mem numArestas
            completar(g, numArestas, usadas, rnd);

            // DESCONEXO
            // Separar os vertices em 2 times , A e B.
            // Crirar arestas só dentro do tima A e só dentro do time B, nunca criar de A para B
        } else {
            if (numNos < 2) {
                throw new IllegalArgumentException("Um grafo desconexo precisa de pelo menos 2 nós.");
            }

            // Quantos vértices em cada time ?
            int k = escolherCorte(numNos, numArestas, direcionado, rnd);
            // Capacidade de cada time
            long capA = maxArestas(k, direcionado);
            long capB = maxArestas(numNos - k, direcionado);

            // Divide as arestas proporcionalmente à capacidade de cada grupo.
            long noA = Math.round((double) numArestas * capA / (capA + capB));
            noA = Math.min(noA, capA);
            long noB = numArestas - noA;
            if (noB > capB) {
                noA += noB - capB;
                noB = capB;
            }

            // Quais vértices vão para qual time ?
            int[] ordem = permutacao(numNos, rnd);
            // Crie 3 arestas aleatórias usando só os vértices das posições 0 a 2 da ordem” = time A
            completarGrupo(g, (int) noA, ordem, 0, k, usadas, rnd);
            completarGrupo(g, (int) noB, ordem, k, numNos, usadas, rnd);
        }
        return g;
    }

    public static long maxArestas(long n, boolean direcionado) {
        long pares = n * (n - 1);
        return direcionado ? pares : pares / 2;
    }

    // auxiliares
    // Ele que vai decidir quantos vértices vão para o A , o resto vai para o B
    private static int escolherCorte(int n, int m, boolean direcionado, Random rnd) {
        // Sorteia um tamanho
        int k = 1 + rnd.nextInt(n / 2);
        // Se as arestas não couberem , dimunuir o K
        while (k > 1 && maxArestas(k, direcionado) + maxArestas(n - k, direcionado) < m) {
            k--;
        }
        // Serve para descobrir o por que o while parou
        if (maxArestas(k, direcionado) + maxArestas(n - k, direcionado) < m) {
            throw new IllegalArgumentException("Com " + n + " nós, um grafo desconexo comporta no máximo "
                    + (maxArestas(1, direcionado) + maxArestas(n - 1, direcionado)) + " arestas.");
        }
        return k;
    }

    // Conexo, cria uma lista com todos os vértices
    private static void completar(Grafo g, int alvo, Set<Long> usadas, Random rnd) {
        int[] ids = new int[g.getNumVertices()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i;
        }
        adicionarAleatorias(g, alvo - g.getNumArestas(), ids, usadas, rnd);
    }

    // Desconexo, recebe a aordem embaralhada e o pedaço que ainda interessa (ini até fim)
    private static void completarGrupo(Grafo g, int quantidade, int[] ordem, int ini, int fim,
                                       Set<Long> usadas, Random rnd) {
        int[] ids = new int[fim - ini];
        // Copia só o pedaõ do time para uma lista nova
        System.arraycopy(ordem, ini, ids, 0, ids.length);
        adicionarAleatorias(g, quantidade, ids, usadas, rnd);
    }

    // Cria a quantidade de arestas novas usando só os svértices da lista ids
    private static void adicionarAleatorias(Grafo g, int quantidade, int[] ids, Set<Long> usadas, Random rnd) {
        if (quantidade <= 0) {
            return;
        }
        // Mede o quanto o grupo ta cheio
        int k = ids.length;
        boolean dir = g.isDirecionado();
        long capacidade = maxArestas(k, dir);
        long jaUsadas = 0;
        // Conta quantas arestas o grupo já tem
        for (int a : ids) {
            jaUsadas += g.getGrauSaida(a);
        }
        if (!dir) {
            jaUsadas /= 2;
        }
        // O grupo vai ficar mais da metade cheio ?
        if (jaUsadas + quantidade > capacidade / 2) {
            // Modo denso: lista todos os pares livres, embaralha e pega os primeiros.
            long livres = capacidade - jaUsadas;
            long[] pares = new long[(int) livres];
            int p = 0;
            // Anota todos os pares livres
            // Os 2 for passam por todos os pares de vértices do grupo
            for (int i = 0; i < k; i++) {
                for (int j = dir ? 0 : i + 1; j < k; j++) {
                    if (i == j) {
                        continue;
                    }
                    if (!usadas.contains(chave(ids[i], ids[j], dir, g.getNumVertices()))) {
                        // Aqui junta os bits em um long
                        pares[p++] = ((long) ids[i] << 32) | ids[j];
                    }
                }
            }
            // Sorteia entre os livres, sem repretir
            for (int i = 0; i < quantidade; i++) {
                int r = i + rnd.nextInt(p - i);
                long t = pares[i];
                pares[i] = pares[r];
                pares[r] = t;
                // Isso aqui ele ta desempacotando o par 32 bits de cima = a 32 bits de baixo = b
                // pares[i] >>> 32) emuprra tudo 32 casas para a direita, a metade da baixo 'b' cai pra fora e some, o a desce para o lugar dele
                // pares[i] & 0xffffffffL o & bit a bit compara casa por casa e só mantém o bit onde os dois números tem 1
                // 0xffffffffl é um número com 32 zeros em cima e 32 uns em baixo
                // Onde a máscara tem 0, o resultado vira 0 e o a é apagado
                // Onde a máscara tem 1, o resultado vira 1 e o b sobrevive
                inserir(g, (int) (pares[i] >>> 32), (int) (pares[i] & 0xffffffffL), usadas, rnd);
            }
        } else {
            // Modo normal, sorteia 2 vértices da lista, o rnd sorteia uma posição e o ids[...] pega o vértice dessa posição
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

    // O conjunto usadas precisa guardar as arestas que já existem.
    // Guardar um número por aresta é mais simples e rápido que guardar um par.
    // Como se fosse a chave o HashMap
    private static long chave(int a, int b, boolean direcionado, int n) {
        if (!direcionado && a > b) {
            int t = a;
            a = b;
            b = t;
        }
        return (long) a * n + b;
    }

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
