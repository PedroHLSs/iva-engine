package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Regra R03: se o cClassTrib do item é de benefício, o NCM está num dos anexos que o catálogo admite para aquele código? Gravidade: grave. Até a 1.0.0 bastava o NCM estar em qualquer anexo; desde a 1.1.0 (30/09/2026) o catálogo declara os anexos admitidos de cada código, NENHUM quer dizer que o código não exige anexo, e não declarado é NAO_AVALIADO. Desde 01/10/2026 a cobertura é por anexo (decisão D8): CONFORME se o NCM está num anexo admitido e carregado na data; ACHADO só se todos os admitidos estão carregados e o NCM não está em nenhum; qualquer outro caso é NAO_AVALIADO.
public final class RegraBeneficioExigeNcmEmAnexo extends RegraDeItem {

    public static final String ID = "R03";
    public static final String VERSAO = "1.1.0";

    private final ProcedenciaNormativa cobertura;
    private final List<AnexoDeclarado> anexosDeclarados;

    // Construtor que recebe o período coberto pela tabela de anexos; sem anexos declarados, como numa carga gravada antes de 01/10/2026.
    public RegraBeneficioExigeNcmEmAnexo(ProcedenciaNormativa coberturaDaTabelaDeAnexos) {
        this(coberturaDaTabelaDeAnexos, List.of());
    }

    // Construtor que recebe também os anexos declarados pela carga, com o período em que cada um está carregado.
    public RegraBeneficioExigeNcmEmAnexo(
            ProcedenciaNormativa coberturaDaTabelaDeAnexos, List<AnexoDeclarado> anexosDeclarados) {
        this.cobertura = exigirCobertura(coberturaDaTabelaDeAnexos, "itens de anexo");
        if (anexosDeclarados == null) {
            throw new RegraInvalida(
                    "A regra %s precisa da lista de anexos declarados, vazia quando não há nenhum.".formatted(ID));
        }
        this.anexosDeclarados = List.copyOf(anexosDeclarados);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String versao() {
        return VERSAO;
    }

    @Override
    public Severidade severidade() {
        return Severidade.GRAVE;
    }

    // Aplica a regra: só cobra o anexo quando o catálogo diz que o cClassTrib é de benefício e declara os anexos admitidos, e aponta se o NCM não estiver em nenhum deles.
    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Optional<CodigoClassificacaoTributaria> codigo = item.codigoClassificacaoTributaria();
        if (codigo.isEmpty()) {
            return naoAvaliada(item, documento,
                    "O item não declarou cClassTrib; não há como saber se um benefício foi invocado.");
        }

        Optional<ClassificacaoTributaria> registro = contexto.classificacaoTributaria(codigo.get());
        if (registro.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O catálogo nada diz sobre o cClassTrib \"%s\" na data de emissão, então não há como "
                            + "saber se ele indica benefício.").formatted(codigo.get().valor()));
        }

        ClassificacaoTributaria classificacao = registro.get();
        if (!classificacao.indicadorDeBeneficio()) {
            // O código não é de benefício, então não precisa de anexo e o item passa na regra.
            return conforme(item, documento);
        }

        if (classificacao.anexosAdmitidos().isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O catálogo não declara os anexos admitidos (anexosAdmitidos) do cClassTrib \"%s\", "
                            + "marcado como benefício; sem eles não há como saber em que anexo o NCM deveria "
                            + "estar.").formatted(codigo.get().valor()));
        }
        Set<IdentificadorAnexo> admitidos = classificacao.anexosAdmitidos().get();
        if (admitidos.isEmpty()) {
            // NENHUM: o catálogo declara que o benefício deste código não depende de anexo.
            return conforme(item, documento);
        }

        Optional<Ncm> ncm = item.ncm();
        if (ncm.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O item invoca o cClassTrib \"%s\", marcado como benefício, mas não declarou NCM; "
                            + "sem NCM não há o que procurar nos anexos.").formatted(codigo.get().valor()));
        }
        if (!cobertura.vigenteEm(documento.dataEmissao())) {
            return naoAvaliada(item, documento,
                    ("A tabela de itens de anexo carregada cobre a partir de %s%s e não alcança a data de "
                            + "emissão %s. Sem cobertura, não constar de anexo é falta de dado, não ausência "
                            + "de vínculo.").formatted(
                            cobertura.vigenciaInicio(),
                            cobertura.vigenciaFim().map(" até %s"::formatted).orElse(""),
                            documento.dataEmissao()));
        }

        List<String> anexos = AnexosDoItem.identificadoresOrdenados(contexto, ncm.get());
        List<String> anexosAdmitidos = admitidos.stream().map(IdentificadorAnexo::valor).sorted().toList();
        Set<String> carregados = anexosDeclarados.stream()
                .filter(anexo -> anexo.carregadoEm(documento.dataEmissao()))
                .map(anexo -> anexo.identificador().valor())
                .collect(Collectors.toSet());
        if (anexos.stream().anyMatch(anexo -> anexosAdmitidos.contains(anexo) && carregados.contains(anexo))) {
            return conforme(item, documento);
        }
        List<String> naoCarregados = anexosAdmitidos.stream().filter(anexo -> !carregados.contains(anexo)).toList();
        if (!naoCarregados.isEmpty()) {
            // Cobertura por anexo (D8): o NCM pode estar justamente no anexo que a carga não trouxe.
            return naoAvaliada(item, documento,
                    ("O cClassTrib \"%s\" admite anexo que a carga não declara carregado na data de emissão %s: "
                            + "anexo admitido não carregado [%s]. Sem os itens desse anexo, o NCM fora dos "
                            + "anexos carregados é falta de dado, não ausência de vínculo.")
                            .formatted(codigo.get().valor(), documento.dataEmissao(),
                                    String.join(", ", naoCarregados)));
        }

        return comAchado(
                item,
                documento,
                List.of(
                        doDocumento("cClassTrib", item, codigo.get().valor()),
                        doDocumento("ncm", item, ncm.get().valor()),
                        daTabela(
                                "itemAnexo",
                                AnexosDoItem.TABELA,
                                cobertura.fonteNormativa(),
                                Optional.of(anexos.isEmpty() ? "nenhum anexo" : String.join(" | ", anexos)),
                                Optional.of(String.join(" | ", anexosAdmitidos)))),
                classificacao.dispositivoLegal(),
                classificacao.vigencia(),
                ValorEmRisco.naoCalculavel(
                        "Apurar a diferença exigiria saber qual tratamento caberia ao item, e o catálogo "
                                + "não vincula este NCM a nenhum dos anexos admitidos para o código na data."));
    }
}
