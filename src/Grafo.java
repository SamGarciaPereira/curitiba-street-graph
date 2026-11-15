import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Grafo {
    private final boolean direcionado;
    private final List<String> rotulos = new ArrayList<>();
    // Lista de listas,  adjacencia.get(i) é a lista de arestas que saem do vértice i
    private final List<List<Aresta>> adjacencia = new ArrayList<>();
    private int numArestas = 0;

    public Grafo(boolean direcionado) {
        this.direcionado = direcionado;
    }

    // Cria um Grafo já rotulado e com o número de arestas
    public Grafo(int n, boolean direcionado) {
        this(direcionado);
        for (int i = 0; i < n; i++) {
            adicionarVertice(String.valueOf(i + 1));
        }
    }

    // Adiciona um vértice e devolve o seu índice
    public int adicionarVertice(String rotulo) {
        rotulos.add(rotulo);
        adjacencia.add(new ArrayList<>());
        return rotulos.size() - 1;
    }

    // Valida os vértices, adiciona na lista de origem. Caso seja não direcionado, também vai colocar
    // na lista de destino uma aresta que aponta de volta para a origem. Só adiciona +1 no numArestas.
    public void adicionarAresta(int origem, int destino, double peso, String rotulo) {
        validarVertice(origem);
        validarVertice(destino);
        adjacencia.get(origem).add(new Aresta(destino, peso, rotulo));
        if (!direcionado && origem != destino) {
            adjacencia.get(destino).add(new Aresta(origem, peso, rotulo));
        }
        numArestas++;
    }

    // Percorre a lista da origem procurando alguma aresta com aquele destino.
    public boolean existeAresta(int origem, int destino) {
        validarVertice(origem);
        validarVertice(destino);
        for (Aresta a : adjacencia.get(origem)) {
            if (a.getDestino() == destino) {
                return true;
            }
        }
        return false;
    }

    public boolean isDirecionado() {
        return direcionado;
    }

    public int getNumVertices() {
        return rotulos.size();
    }

    public int getNumArestas() {
        return numArestas;
    }

    public String getRotulo(int vertice) {
        validarVertice(vertice);
        return rotulos.get(vertice);
    }

    public List<Aresta> getAdjacentes(int vertice) {
        validarVertice(vertice);
        // Esse unmodifiableList não permite dar add / remove
        return Collections.unmodifiableList(adjacencia.get(vertice));
    }

    // Tamanho da lista de v , no grafo não direcionado , isso já é o seu  grau
    public int getGrauSaida(int vertice) {
        validarVertice(vertice);
        return adjacencia.get(vertice).size();
    }

    // Percorre todas as arestas do grafo e contam quantas vezes chegam em cada vértice.
    // Retorna um vetor com essa contagem.
    public int[] getGrausEntrada() {
        int[] graus = new int[getNumVertices()];
        for (List<Aresta> lista : adjacencia) {
            for (Aresta a : lista) {
                graus[a.getDestino()]++;
            }
        }
        return graus;
    }

    private void validarVertice(int v) {
        if (v < 0 || v >= rotulos.size()) {
            throw new IndexOutOfBoundsException("Vértice inválido: " + v + " (n=" + rotulos.size() + ")");
        }
    }

    // Resumo completo do grafo criado
    public String resumo() {
        int n = getNumVertices();
        int maxGrau = 0;
        long somaGraus = 0;
        int isolados = 0;
        int[] entrada = getGrausEntrada();
        for (int v = 0; v < n; v++) {
            int g = adjacencia.get(v).size();
            somaGraus += g;
            maxGrau = Math.max(maxGrau, g);
            if (g == 0 && entrada[v] == 0) {
                isolados++;
            }
        }
        double grauMedio = n == 0 ? 0 : (double) somaGraus / n;
        return String.format(java.util.Locale.US,
                "Tipo: %s%nVértices: %d%nArestas: %d%nGrau%s médio: %.2f | máximo: %d%nVértices isolados: %d",
                direcionado ? "direcionado" : "não direcionado",
                n, numArestas,
                direcionado ? " de saída" : "",
                grauMedio, maxGrau, isolados);
    }
}
