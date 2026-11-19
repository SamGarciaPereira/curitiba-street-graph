import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Grafo {
    private final boolean direcionado;
    private final List<String> rotulos = new ArrayList<>();
    // lista de adjacência: adjacencia.get(i) são as arestas que saem do vértice i
    private final List<List<Aresta>> adjacencia = new ArrayList<>();
    private int numArestas = 0;

    public Grafo(boolean direcionado) {
        this.direcionado = direcionado;
    }

    // cria um grafo com n vértices já rotulados "1".."n"
    public Grafo(int n, boolean direcionado) {
        this(direcionado);
        for (int i = 0; i < n; i++) {
            adicionarVertice(String.valueOf(i + 1));
        }
    }

    // adiciona um vértice e devolve o seu índice
    public int adicionarVertice(String rotulo) {
        rotulos.add(rotulo);
        adjacencia.add(new ArrayList<>());
        return rotulos.size() - 1;
    }

    // adiciona a aresta na lista da origem; se não direcionado, adiciona também a volta
    // (destino->origem). conta só +1 em numArestas (uma aresta lógica)
    public void adicionarAresta(int origem, int destino, double peso, String rotulo) {
        validarVertice(origem);
        validarVertice(destino);
        adjacencia.get(origem).add(new Aresta(destino, peso, rotulo));
        if (!direcionado && origem != destino) {
            adjacencia.get(destino).add(new Aresta(origem, peso, rotulo));
        }
        numArestas++;
    }

    // procura na lista da origem uma aresta para aquele destino
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
        // unmodifiableList: impede add/remove de fora
        return Collections.unmodifiableList(adjacencia.get(vertice));
    }

    // tamanho da lista de v; no grafo não direcionado isso já é o grau
    public int getGrauSaida(int vertice) {
        validarVertice(vertice);
        return adjacencia.get(vertice).size();
    }

    // conta quantas arestas chegam em cada vértice (grau de entrada)
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

    // resumo do grafo: tipo, nº de vértices/arestas, grau médio/máximo e isolados
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
