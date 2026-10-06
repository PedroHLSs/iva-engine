package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// D026 (04/10/2026): acervo de cargas em memória, que também grava como o repositório da importação completa, para os testes que não precisam de banco. Cada gravação ganha um instante um minuto depois da anterior, e a mais recente é a de instante maior, desempatada pela versão, como em AcervoDeCargasNoBanco. Valores fictícios.
public final class AcervoDeCargasEmMemoriaParaTeste implements AcervoDeCargas, RepositorioDeCargaDeCatalogo {

    private static final Instant INICIO = Instant.parse("1900-01-01T00:00:00Z");

    private final Map<String, CargaDeCatalogo> cargas = new LinkedHashMap<>();
    private final Map<String, Instant> importadas = new LinkedHashMap<>();
    private final Map<String, Instant> alteradas = new LinkedHashMap<>();
    private final Map<String, Instant> selos = new LinkedHashMap<>();
    private final Map<String, String> origens = new LinkedHashMap<>();
    private Instant agora = INICIO;
    private int travasDoAcervo;
    private boolean esvaziarAoTravar;

    // Grava a carga como importada agora; a origem só existe quando ela foi derivada de outra.
    public void gravar(CargaDeCatalogo carga, Optional<String> origem) {
        agora = agora.plus(Duration.ofMinutes(1));
        cargas.put(carga.versao(), carga);
        importadas.put(carga.versao(), agora);
        origem.ifPresent(versao -> origens.put(carga.versao(), versao));
    }

    // Sela a carga, como se uma análise a tivesse usado.
    public void selar(String versao) {
        selos.put(versao, agora);
    }

    // Faz a próxima trava do acervo encontrar o acervo vazio, como se todas as cargas tivessem sido excluídas entre a leitura da tela e a trava.
    public void esvaziarNaProximaTrava() {
        esvaziarAoTravar = true;
    }

    // Diz quantas vezes a trava do acervo foi tomada.
    public int travasDoAcervo() {
        return travasDoAcervo;
    }

    // Devolve a versão de origem gravada para a carga, se ela foi derivada de outra.
    public Optional<String> origemDe(String versao) {
        return Optional.ofNullable(origens.get(versao));
    }

    @Override
    public void salvar(CargaDeCatalogo carga) {
        if (cargas.containsKey(carga.versao())) {
            throw new CatalogoInvalido(
                    "Já existe carga de catálogo com a versão \"%s\".".formatted(carga.versao()));
        }
        gravar(carga, Optional.empty());
    }

    @Override
    public Optional<String> versaoDaCargaMaisRecente() {
        return maisRecente();
    }

    @Override
    public List<EstadoDaCarga> listar() {
        return ordem().stream().map(versao -> estado(versao).orElseThrow()).toList();
    }

    @Override
    public Optional<EstadoDaCarga> estado(String versao) {
        CargaDeCatalogo carga = cargas.get(versao);
        if (carga == null) {
            return Optional.empty();
        }
        return Optional.of(new EstadoDaCarga(versao, importadas.get(versao),
                Optional.ofNullable(selos.get(versao)), 0L,
                Optional.ofNullable(origens.get(versao)), Optional.ofNullable(alteradas.get(versao)),
                maisRecente().orElseThrow().equals(versao),
                carga.classificacoesTributarias().size(), carga.registrosDeNcm().size(),
                carga.itensDeAnexo().size(), carga.aliquotas().size(), carga.natureza()));
    }

    @Override
    public Optional<CargaDeCatalogo> conteudo(String versao) {
        return Optional.ofNullable(cargas.get(versao));
    }

    @Override
    public boolean existeVersao(String versao) {
        return cargas.containsKey(versao);
    }

    @Override
    public <T> T comCargaTravada(String versao, Function<EstadoDaCarga, T> operacao) {
        return operacao.apply(estado(versao).orElseThrow(() -> new CargaNaoEncontrada(versao)));
    }

    @Override
    public <T> T comAcervoTravado(Function<Optional<EstadoDaCarga>, T> operacao) {
        travasDoAcervo++;
        if (esvaziarAoTravar) {
            esvaziarAoTravar = false;
            List.copyOf(cargas.keySet()).forEach(this::excluir);
        }
        return operacao.apply(maisRecente().flatMap(this::estado));
    }

    @Override
    public void substituirConteudo(String versao, CargaDeCatalogo nova) {
        if (selos.containsKey(versao)) {
            throw new CargaSelada("selada");
        }
        agora = agora.plus(Duration.ofMinutes(1));
        cargas.put(versao, nova);
        alteradas.put(versao, agora);
    }

    @Override
    public void salvarDerivada(CargaDeCatalogo nova, String versaoDeOrigem) {
        if (!cargas.containsKey(versaoDeOrigem)) {
            throw new CargaNaoEncontrada(versaoDeOrigem);
        }
        salvarSemRepetir(nova);
        origens.put(nova.versao(), versaoDeOrigem);
    }

    @Override
    public void excluir(String versao) {
        if (selos.containsKey(versao)) {
            throw new CargaSelada("selada");
        }
        cargas.remove(versao);
        importadas.remove(versao);
        alteradas.remove(versao);
        origens.remove(versao);
    }

    // Método auxiliar que grava a carga nova e recusa versão repetida.
    private void salvarSemRepetir(CargaDeCatalogo nova) {
        if (cargas.containsKey(nova.versao())) {
            throw new CatalogoInvalido(
                    "Já existe carga de catálogo com a versão \"%s\".".formatted(nova.versao()));
        }
        gravar(nova, Optional.empty());
    }

    // Método auxiliar que devolve a versão da carga mais recente.
    private Optional<String> maisRecente() {
        List<String> ordem = ordem();
        return ordem.isEmpty() ? Optional.empty() : Optional.of(ordem.get(0));
    }

    // Método auxiliar que ordena as versões da mais recente para a mais antiga.
    private List<String> ordem() {
        List<String> versoes = new ArrayList<>(cargas.keySet());
        versoes.sort(Comparator.comparing((String versao) -> importadas.get(versao))
                .thenComparing(Comparator.naturalOrder()).reversed());
        return versoes;
    }
}
