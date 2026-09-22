package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.util.Comparator;
import java.util.Optional;

/**
 * O que junta produtos num mesmo grupo: enquadramento declarado e situação.
 *
 * <h2>Por que agrupar assim, e não listar nota por nota</h2>
 *
 * <p>Quem trabalha no fiscal corrige cadastro, não nota. Um NCM classificado
 * errado aparece em quatrocentas notas e continua sendo <strong>um</strong> erro
 * de parametrização. Uma lista de quatrocentas linhas iguais esconde isso: ela
 * mostra o tamanho do estrago e não mostra a causa, e leva a pessoa a conferir
 * quatrocentas vezes a mesma coisa.</p>
 *
 * <p>A situação entra na chave porque o mesmo NCM com o mesmo {@code cClassTrib}
 * pode ter desfechos diferentes em notas diferentes — datas de emissão
 * diferentes, campos preenchidos diferentes. Misturar os desfechos num grupo só
 * faria a tela ter de escolher um deles para mostrar, e qualquer escolha estaria
 * errada para parte do grupo.</p>
 */
public record ChaveDoGrupo(
        Optional<String> ncm,
        Optional<String> cClassTrib,
        EstadoDeConferencia situacao) {

    /** Ordem estável para desempatar grupos de mesmo valor e mesma contagem. */
    public static final Comparator<ChaveDoGrupo> ORDEM_ESTAVEL =
            Comparator.comparing((ChaveDoGrupo chave) -> chave.ncm().orElse(""))
                    .thenComparing(chave -> chave.cClassTrib().orElse(""))
                    .thenComparingInt(chave -> chave.situacao().ordinal());

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

    /** A chave deste produto: o que o documento declarou, mais a situação apurada. */
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

    /** Quanto da chave o documento deu, escrito por extenso no grupo. */
    public NivelDoAgrupamento nivel() {
        return NivelDoAgrupamento.de(ncm.isPresent(), cClassTrib.isPresent());
    }
}
