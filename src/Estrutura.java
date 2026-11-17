import java.util.ArrayList;
import java.util.List;

/**
 * Funções de estrutura do grafo:
 * - Verificar se o grafo é conexo
 * - Mostrar componentes (fracamente conectados se direcionado)
 * - Verificar se o grafo é Euleriano
 * - Verificar se o grafo é cíclico
 */

// Reúne os 4 algoritmos da parte de estrutura.
// Como o grafo de Curitiba tem mais de 36 mil vértices, as buscas usam fila/pilha
// em vetor (int[]) em vez de recursão, evitando estourar a pilha do Java (StackOverflowError).

public final class Estrutura {

    private Estrutura() {
    }

    // ------------------------------------------------------------------ 6. Conexo

    // Verifica se o grafo é conexo.
    // Se for direcionado, considera conexidade fraca (ignora a direção das arestas),
    // mantendo o mesmo critério usado no GeradorAleatorio e na listagem de componentes.
    public static boolean ehConexo(Grafo g) {
        if (g.getNumVertices() == 0) {
            return true;
        }
        return componentes(g).size() == 1;
    }

    // ------------------------------------------------------------------ 7. Componentes

    // Devolve uma lista onde cada elemento é uma lista com os vértices daquele componente.
    // Em grafos direcionados, encontra os componentes fracamente conectados:
    // para isso, monta uma lista de vizinhos ignorando o sentido da seta (ida e volta).
    public static List<List<Integer>> componentes(Grafo g) {
        int n = g.getNumVertices();
        List<List<Integer>> listaComponentes = new ArrayList<>();
        if (n == 0) {
            return listaComponentes;
        }

        // Se for direcionado, precisamos saber tanto quem sai quanto quem chega no vértice
        // para caminhar nos dois sentidos (conexidade fraca).
        List<List<Integer>> vizinhosSemDirecao = null;
        if (g.isDirecionado()) {
            vizinhosSemDirecao = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                vizinhosSemDirecao.add(new ArrayList<>());
            }
            for (int u = 0; u < n; u++) {
                for (Aresta a : g.getAdjacentes(u)) {
                    int v = a.getDestino();
                    vizinhosSemDirecao.get(u).add(v);
                    vizinhosSemDirecao.get(v).add(u);
                }
            }
        }

        boolean[] visitado = new boolean[n];
        int[] fila = new int[n];

        // Passa por cada vértice; se ainda não foi visitado, começa uma busca em largura (BFS)
        // para descobrir todos os vértices que estão no mesmo "pedaço" (componente) que ele.
        for (int i = 0; i < n; i++) {
            if (visitado[i]) {
                continue;
            }
            List<Integer> comp = new ArrayList<>();
            int ini = 0;
            int fim = 0;

            visitado[i] = true;
            fila[fim++] = i;

            while (ini < fim) {
                int u = fila[ini++];
                comp.add(u);

                if (g.isDirecionado()) {
                    for (int v : vizinhosSemDirecao.get(u)) {
                        if (!visitado[v]) {
                            visitado[v] = true;
                            fila[fim++] = v;
                        }
                    }
                } else {
                    for (Aresta a : g.getAdjacentes(u)) {
                        int v = a.getDestino();
                        if (!visitado[v]) {
                            visitado[v] = true;
                            fila[fim++] = v;
                        }
                    }
                }
            }
            listaComponentes.add(comp);
        }

        // Ordena do maior componente para o menor (deixa a exibição mais limpa no menu)
        listaComponentes.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return listaComponentes;
    }

    // ------------------------------------------------------------------ 8. Euleriano

    // Verifica se o grafo possui um ciclo Euleriano (passa por todas as arestas exatamente uma vez e volta ao início).
    // - Não direcionado: conexo (ignorando vértices isolados) e todos os vértices com grau par.
    // - Direcionado: fortemente conexo nos vértices com arestas e grauEntrada[v] == grauSaida[v] em todos os vértices.
    public static boolean ehEuleriano(Grafo g) {
        int n = g.getNumVertices();
        if (n == 0 || g.getNumArestas() == 0) {
            return false;
        }

        int[] entrada = g.getGrausEntrada();

        if (!g.isDirecionado()) {
            // 1º critério (não direcionado): nenhum vértice pode ter grau ímpar
            for (int v = 0; v < n; v++) {
                if (g.getGrauSaida(v) % 2 != 0) {
                    return false;
                }
            }
            // 2º critério: todos os vértices que têm aresta precisam estar no mesmo componente
            return verticesComArestasConectados(g, entrada);
        } else {
            // 1º critério (direcionado): em todo vértice, o grau de entrada deve ser igual ao de saída
            for (int v = 0; v < n; v++) {
                if (entrada[v] != g.getGrauSaida(v)) {
                    return false;
                }
            }
            // 2º critério: se todo vértice tem grauEntrada == grauSaida e os vértices com arestas
            // estão fracamente conectados, o grafo também é fortemente conexo nesses vértices.
            return verticesComArestasConectados(g, entrada);
        }
    }

    // Conta quantos vértices impedem o grafo de ser Euleriano (útil para explicar o motivo no menu)
    public static int contarVerticesDesbalanceadosEuler(Grafo g) {
        int n = g.getNumVertices();
        int[] entrada = g.getGrausEntrada();
        int cont = 0;
        for (int v = 0; v < n; v++) {
            if (g.isDirecionado()) {
                if (entrada[v] != g.getGrauSaida(v)) {
                    cont++;
                }
            } else {
                if (g.getGrauSaida(v) % 2 != 0) {
                    cont++;
                }
            }
        }
        return cont;
    }

    // Verifica se todos os vértices que possuem pelo menos 1 aresta pertencem a um único componente
    private static boolean verticesComArestasConectados(Grafo g, int[] entrada) {
        List<List<Integer>> comps = componentes(g);
        int compComAresta = 0;
        for (List<Integer> comp : comps) {
            boolean temAresta = false;
            for (int v : comp) {
                if (g.getGrauSaida(v) > 0 || entrada[v] > 0) {
                    temAresta = true;
                    break;
                }
            }
            if (temAresta) {
                compComAresta++;
                if (compComAresta > 1) {
                    return false;
                }
            }
        }
        return compComAresta == 1;
    }

    // ------------------------------------------------------------------ 9. Cíclico

    // Verifica se o grafo possui pelo menos um ciclo.
    // - Direcionado: usa o algoritmo de Kahn (ordenação topológica por remoção de graus de entrada 0).
    //   Se sobrar algum vértice que não pôde ser removido, existe pelo menos um ciclo direcionado.
    // - Não direcionado: usa busca em profundidade (DFS) guardando o pai de cada vértice.
    //   Se encontrar um vizinho já visitado que não é o pai (ou uma segunda aresta paralela para o pai), há ciclo.
    public static boolean ehCiclico(Grafo g) {
        int n = g.getNumVertices();
        if (n == 0 || g.getNumArestas() == 0) {
            return false;
        }

        if (g.isDirecionado()) {
            return ehCiclicoDirecionado(g);
        } else {
            return ehCiclicoNaoDirecionado(g);
        }
    }

    // Algoritmo de Kahn: coloca na fila quem tem grau de entrada 0 e vai "apagando" as arestas que saem dele.
    // Grafo acíclico (DAG) consegue visitar todos os n vértices. Se parar antes de n, tem ciclo.
    private static boolean ehCiclicoDirecionado(Grafo g) {
        int n = g.getNumVertices();
        int[] grauEntrada = g.getGrausEntrada();
        int[] fila = new int[n];
        int ini = 0;
        int fim = 0;

        for (int v = 0; v < n; v++) {
            if (grauEntrada[v] == 0) {
                fila[fim++] = v;
            }
        }

        int visitados = 0;
        while (ini < fim) {
            int u = fila[ini++];
            visitados++;
            for (Aresta a : g.getAdjacentes(u)) {
                int v = a.getDestino();
                grauEntrada[v]--;
                if (grauEntrada[v] == 0) {
                    fila[fim++] = v;
                }
            }
        }

        return visitados < n;
    }

    // DFS iterativa para grafo não direcionado.
    // Trata laços (u == v), arestas paralelas (duas ruas diferentes ligando os mesmos 2 nós) e ciclos de 3+ nós.
    private static boolean ehCiclicoNaoDirecionado(Grafo g) {
        int n = g.getNumVertices();
        // estado: 0 = não visitado, 1 = na pilha (em processamento), 2 = finalizado
        int[] estado = new int[n];
        int[] pai = new int[n];
        // guarda em qual índice da lista de adjacência cada vértice parou (simula a recursão sem estourar pilha)
        int[] indiceVizinho = new int[n];
        boolean[] viuPai = new boolean[n];
        int[] pilha = new int[n];

        for (int i = 0; i < n; i++) {
            pai[i] = -1;
        }

        for (int inicio = 0; inicio < n; inicio++) {
            if (estado[inicio] != 0) {
                continue;
            }

            int topo = 0;
            pilha[topo++] = inicio;
            estado[inicio] = 1;

            while (topo > 0) {
                int u = pilha[topo - 1];
                List<Aresta> adj = g.getAdjacentes(u);

                if (indiceVizinho[u] < adj.size()) {
                    int v = adj.get(indiceVizinho[u]).getDestino();
                    indiceVizinho[u]++;

                    // Laço (aresta de u para o próprio u)
                    if (v == u) {
                        return true;
                    }

                    // Se v é o pai de u na árvore da busca: a primeira aresta é só a volta da mesma rua,
                    // mas se aparecer uma segunda aresta para o mesmo pai, são arestas paralelas (ciclo de 2 nós).
                    if (v == pai[u]) {
                        if (viuPai[u]) {
                            return true;
                        }
                        viuPai[u] = true;
                        continue;
                    }

                    if (estado[v] == 0) {
                        pai[v] = u;
                        estado[v] = 1;
                        pilha[topo++] = v;
                    } else {
                        // Encontrou um vértice já visitado que não é o pai -> fechou ciclo
                        return true;
                    }
                } else {
                    estado[u] = 2;
                    topo--;
                }
            }
        }
        return false;
    }
}
