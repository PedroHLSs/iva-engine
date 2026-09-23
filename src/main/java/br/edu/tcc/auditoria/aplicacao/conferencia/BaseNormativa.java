package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativoNaData;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Classe que representa uma carga de catálogo carregada uma vez e consultada por data; carga ausente vira motivo em cada bloco, não exceção.
public final class BaseNormativa {

    private static final String SEM_NCM_DECLARADO =
            "o documento não declarou NCM para este item, e sem NCM não há o que procurar na tabela";
    private static final String SEM_CLASSTRIB_DECLARADO =
            "o documento não declarou cClassTrib para este item, e é ele que liga o item ao que a "
                    + "carga diz sobre dispositivo, CST admitido e redução";

    private final String versaoDoCatalogo;
    private final Optional<CatalogoParaAuditoria> catalogo;

    // Construtor privado; a base é criada por daVersao ou por nenhumaCargaImportada.
    private BaseNormativa(String versaoDoCatalogo, Optional<CatalogoParaAuditoria> catalogo) {
        this.versaoDoCatalogo = versaoDoCatalogo;
        this.catalogo = catalogo;
    }

    // Método estático que carrega a carga daquela versão, ou registra a ausência dela.
    public static BaseNormativa daVersao(
            ProvedorDeCatalogoPorVersao catalogos, String versaoDoCatalogo) {

        if (catalogos == null) {
            throw new ConferenciaInvalida("Não há provedor de catálogo por versão.");
        }
        if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()) {
            throw new ConferenciaInvalida(
                    "Não há versão de catálogo a carregar. A execução sempre registra a dela.");
        }
        return new BaseNormativa(versaoDoCatalogo, catalogos.daVersao(versaoDoCatalogo));
    }

    // Método estático para quando nenhum catálogo foi importado ainda.
    public static BaseNormativa nenhumaCargaImportada() {
        return new BaseNormativa(SEM_CARGA, Optional.empty());
    }

    // Rótulo que ocupa o lugar da versão quando não há carga nenhuma gravada.
    public static final String SEM_CARGA = "(nenhuma carga importada)";

    public String versaoDoCatalogo() {
        return versaoDoCatalogo;
    }

    public boolean cargaDisponivel() {
        return catalogo.isPresent();
    }

    public Optional<CoberturaDoCatalogo> cobertura() {
        return catalogo.map(CatalogoParaAuditoria::cobertura);
    }

    // Retorna a procedência da carga; sem carga gravada, volta como não declarada.
    public NaturezaDaCarga natureza() {
        return catalogo.map(CatalogoParaAuditoria::natureza)
                .orElseGet(NaturezaDaCarga::naoDeclarada);
    }

    // Retorna o tratamento indicado para o item na data de emissão do documento, bloco a bloco.
    public TratamentoIdentificado tratamentoDe(LocalDate dataDeReferencia, ItemDocumento item) {
        if (dataDeReferencia == null) {
            throw new ConferenciaInvalida(
                    "Sem a data de emissão do documento não há como resolver o catálogo.");
        }
        if (item == null) {
            throw new ConferenciaInvalida("Não há item cujo tratamento identificar.");
        }
        if (catalogo.isEmpty()) {
            return TratamentoIdentificado.naoDeterminado(
                    versaoDoCatalogo, dataDeReferencia, motivoDaCargaAusente());
        }

        return new TratamentoIdentificado(
                versaoDoCatalogo,
                dataDeReferencia,
                item.ncm()
                        .map(ncm -> ncmEm(dataDeReferencia, ncm))
                        .orElseGet(() -> LeituraDoCatalogo.ausente(SEM_NCM_DECLARADO)),
                item.ncm()
                        .map(ncm -> anexosEm(dataDeReferencia, ncm))
                        .orElseGet(() -> LeituraDoCatalogo.ausente(SEM_NCM_DECLARADO)),
                item.codigoClassificacaoTributaria()
                        .map(codigo -> classificacaoEm(dataDeReferencia, codigo))
                        .orElseGet(() -> LeituraDoCatalogo.ausente(SEM_CLASSTRIB_DECLARADO)),
                aliquotasEm(dataDeReferencia));
    }

    // Retorna o que a carga diz sobre o NCM naquela data.
    public LeituraDoCatalogo<DescricaoDeNcm> ncmEm(LocalDate data, Ncm ncm) {
        if (catalogo.isEmpty()) {
            return LeituraDoCatalogo.ausente(motivoDaCargaAusente());
        }
        return LeituraDoCatalogo.deUnico(
                contextoEm(data).registroNcm(exigirNcm(ncm)).map(DescricaoDeNcm::de),
                "a carga %s não traz o NCM %s vigente em %s"
                        .formatted(versaoDoCatalogo, ncm.valor(), data));
    }

    // Retorna a que anexos a carga vincula o NCM naquela data.
    public LeituraDoCatalogo<EnquadramentoDoNcm> anexosEm(LocalDate data, Ncm ncm) {
        if (catalogo.isEmpty()) {
            return LeituraDoCatalogo.ausente(motivoDaCargaAusente());
        }
        List<EnquadramentoDoNcm> encontrados = contextoEm(data).anexosDoNcm(exigirNcm(ncm)).stream()
                .map(EnquadramentoDoNcm::de)
                .sorted(Comparator.comparing(EnquadramentoDoNcm::anexo))
                .toList();
        if (encontrados.isEmpty()) {
            return LeituraDoCatalogo.ausente(
                    "a carga %s não vincula o NCM %s a nenhum anexo vigente em %s"
                            .formatted(versaoDoCatalogo, ncm.valor(), data));
        }
        return LeituraDoCatalogo.de(encontrados);
    }

    // Retorna o que a carga diz sobre o cClassTrib naquela data.
    public LeituraDoCatalogo<ClassificacaoDoCatalogo> classificacaoEm(
            LocalDate data, CodigoClassificacaoTributaria codigo) {

        if (catalogo.isEmpty()) {
            return LeituraDoCatalogo.ausente(motivoDaCargaAusente());
        }
        if (codigo == null) {
            throw new ConferenciaInvalida("Não há cClassTrib a consultar.");
        }
        return LeituraDoCatalogo.deUnico(
                contextoEm(data).classificacaoTributaria(codigo).map(ClassificacaoDoCatalogo::de),
                "a carga %s não traz o cClassTrib %s vigente em %s"
                        .formatted(versaoDoCatalogo, codigo.valor(), data));
    }

    // Retorna as alíquotas vigentes na data, um bloco por tributo, sempre os três e nunca somados.
    public List<TratamentoDeTributo> aliquotasEm(LocalDate data) {
        if (data == null) {
            throw new ConferenciaInvalida(
                    "A consulta à base tributária exige data: sem ela a resposta não significa nada.");
        }
        List<TratamentoDeTributo> porTributo = new ArrayList<>();
        for (Tributo tributo : Tributo.values()) {
            porTributo.add(TratamentoDeTributo.de(tributo, aliquotasDe(tributo, data)));
        }
        return List.copyOf(porTributo);
    }

    // Método auxiliar que busca as alíquotas de um tributo na data, ou o motivo de não haver.
    private LeituraDoCatalogo<AliquotaDoCatalogo> aliquotasDe(Tributo tributo, LocalDate data) {
        if (catalogo.isEmpty()) {
            return LeituraDoCatalogo.ausente(motivoDaCargaAusente());
        }
        List<AliquotaDoCatalogo> aliquotas = contextoEm(data).aliquotas(tributo).stream()
                .map(AliquotaDoCatalogo::de)
                .sorted(Comparator.comparing(AliquotaDoCatalogo::abrangencia))
                .toList();
        if (aliquotas.isEmpty()) {
            return LeituraDoCatalogo.ausente(
                    "a carga %s não traz alíquota deste tributo vigente em %s"
                            .formatted(versaoDoCatalogo, data));
        }
        return LeituraDoCatalogo.de(aliquotas);
    }

    // Método auxiliar que monta o contexto normativo da carga resolvido na data indicada.
    private ContextoNormativo contextoEm(LocalDate data) {
        if (data == null) {
            throw new ConferenciaInvalida("Não há data em que resolver o catálogo.");
        }
        CatalogoParaAuditoria carga = catalogo.orElseThrow();
        return new ContextoNormativoNaData(
                data,
                carga.classificacoesTributarias(),
                carga.registrosDeNcm(),
                carga.itensDeAnexo(),
                carga.aliquotas());
    }

    // Método auxiliar para verificar se o NCM é nulo e lançar uma exceção.
    private static Ncm exigirNcm(Ncm ncm) {
        if (ncm == null) {
            throw new ConferenciaInvalida("Não há NCM a consultar.");
        }
        return ncm;
    }

    // Método auxiliar que escreve o motivo de não haver carga: nenhuma importada, ou a da análise não está mais gravada.
    private String motivoDaCargaAusente() {
        if (SEM_CARGA.equals(versaoDoCatalogo)) {
            return "nenhum catálogo foi importado ainda. Enquanto não houver carga, o sistema não tem "
                    + "base normativa para indicar tratamento nenhum, e não inventa uma";
        }
        return ("a carga de catálogo %s, que esta análise registrou, não está mais gravada. O "
                + "resultado das regras continua valendo, porque foi gravado junto com os "
                + "apontamentos; o que não é possível é reapresentar a base normativa que o "
                + "produziu. Importar uma carga nova não recompõe esta: seria mostrar outra tabela "
                + "no lugar da que valeu.").formatted(versaoDoCatalogo);
    }
}
