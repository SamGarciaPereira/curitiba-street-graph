import java.util.ArrayList;
import java.util.List;

// funções de estrutura do grafo: conexo, componentes, Euleriano e cíclico.
// como o grafo de Curitiba tem mais de 36 mil vértices, as buscas usam fila/pilha em
// vetor (int[]) em vez de recursão, para não estourar a pilha do Java (StackOverflowError).
public final class Estrutura {

    private Estrutura() {
    }

    // ---- 6. conexo

    // verifica se o grafo é conexo.
    // se for direcionado, usa conexidade fraca (ignora a direção), o mesmo critério dos componentes.
    public static boolean ehConexo(Grafo g) {
        if (g.getNumVertices() == 0) {
            return true;
        }
        return componentes(g).size() == 1;
    }

    // ---- 7. componentes

    // devolve a lista de componentes (cada um é a lista de vértices daquele pedaço).
    // no grafo direcionado acha os fracamente conectados: monta os vizinhos ignorando o sentido da seta.
    public static List<List<Integer>> componentes(Grafo g) {
        int n = g.getNumVertices();
        List<List<Integer>> listaComponentes = new ArrayList<>();
        if (n == 0) {
            return listaComponentes;
        }

        // no direcionado precisa dos vizinhos de entrada e de saída para andar nos dois sentidos
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

        // para cada vértice ainda não visitado, uma BFS marca todo o seu componente
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

        // ordena do maior componente para o menor (exibição mais limpa no menu)
        listaComponentes.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return listaComponentes;
    }

    // ---- 8. Euleriano

    // verifica se o grafo tem ciclo Euleriano (passa por toda aresta uma vez e volta ao início).
    // - não direcionado: conexo (fora os isolados) e todo vértice com grau par.
    // - direcionado: grauEntrada == grauSaida em todo vértice e conexo nos vértices com aresta.
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
            // 2º critério: com grauEntrada == grauSaida em todo vértice e os vértices com arestas
            // fracamente conectados, o grafo também é fortemente conexo nesses vértices.
            return verticesComArestasConectados(g, entrada);
        }
    }

    // conta quantos vértices impedem o grafo de ser Euleriano (para explicar o motivo no menu)
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

    // verifica se todos os vértices com pelo menos 1 aresta estão num único componente
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

    // ---- 9. cíclico

    // verifica se o grafo tem pelo menos um ciclo.
    // - direcionado: algoritmo de Kahn (ordenação topológica removendo quem tem grau de entrada 0);
    //   se sobrar vértice sem remover, há ciclo.
    // - não direcionado: DFS guardando o pai; vizinho já visitado que não é o pai (ou 2ª aresta
    //   para o pai) fecha um ciclo.
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

    // Kahn: enfileira quem tem grau de entrada 0 e vai "apagando" as arestas que saem dele.
    // um DAG visita todos os n vértices; se parar antes de n, há ciclo.
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

    // DFS iterativa (não direcionado); trata laços (u == v), arestas paralelas e ciclos de 3+ nós.
    private static boolean ehCiclicoNaoDirecionado(Grafo g) {
        int n = g.getNumVertices();
        // estado: 0 = não visitado, 1 = na pilha (em processamento), 2 = finalizado
        int[] estado = new int[n];
        int[] pai = new int[n];
        // em que índice da adjacência cada vértice parou (simula a recursão sem estourar a pilha)
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

                    // laço (aresta de u para o próprio u)
                    if (v == u) {
                        return true;
                    }

                    // v é o pai de u: a 1ª volta é a mesma aresta; uma 2ª aresta para o pai
                    // são arestas paralelas (ciclo de 2 nós).
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
                        // vértice já visitado que não é o pai -> fechou ciclo
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
