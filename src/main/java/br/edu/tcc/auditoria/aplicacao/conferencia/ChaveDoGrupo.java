package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.util.Comparator;
import java.util.Optional;

// Representa o que junta produtos num mesmo grupo: NCM e cClassTrib declarados, mais a situação apurada.
public record ChaveDoGrupo(
        Optional<String> ncm,
        Optional<String> cClassTrib,
        EstadoDeConferencia situacao) {

    // Ordem estável para desempatar grupos de mesmo valor e mesma contagem.
    public static final Comparator<ChaveDoGrupo> ORDEM_ESTAVEL =
            Comparator.comparing((ChaveDoGrupo chave) -> chave.ncm().orElse(""))
                    .thenComparing(chave -> chave.cClassTrib().orElse(""))
                    .thenComparingInt(chave -> chave.situacao().ordinal());

    // Valida que componente não declarado venha como Optional vazio e que a situação esteja presente.
    public ChaveDoGrupo {
        if (ncm == null || cClassTrib == null) {
            throw new ConferenciaInvalida(
                    "Componente não declarado se representa com Optional.empty(), nunca com nulo. O "
                            + "nível do agrupamento é derivado dessa ausência.");
        }
        if (situacao == null) {
            throw new ConferenciaInvalida("O grupo precisa da situação que ele reúne.");
        }
    }

    // Método estático que cria a chave do produto a partir do que o documento declarou e da situação apurada.
    public static ChaveDoGrupo de(ProdutoConferido produto) {
        if (produto == null) {
            throw new ConferenciaInvalida("Não há produto a agrupar.");
        }
        ItemDocumento item = produto.dados().item();
        return new ChaveDoGrupo(
                item.ncm().map(Ncm::valor),
                item.codigoClassificacaoTributaria().map(CodigoClassificacaoTributaria::valor),
                produto.situacao().situacao());
    }

    // Retorna o nível do agrupamento, isto é, quanto da chave o documento declarou.
    public NivelDoAgrupamento nivel() {
        return NivelDoAgrupamento.de(ncm.isPresent(), cClassTrib.isPresent());
    }
}
