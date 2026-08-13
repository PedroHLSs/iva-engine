package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Implementação do {@link ContextoNormativo} presa a uma data.
 *
 * <p>Recebe a data de referência — a data de emissão do documento auditado — e
 * os repositórios no construtor, e a partir daí só sabe responder naquela data.
 * A data fica em campo privado, sem acessor: nem as regras nem esta própria
 * classe têm como trocá-la depois.</p>
 *
 * <p>Não há construtor sem data, e não há chamada a {@code LocalDate.now()} em
 * lugar nenhum do domínio. Quem constrói o contexto é a camada de aplicação, a
 * partir do documento que está auditando.</p>
 */
public final class ContextoNormativoNaData implements ContextoNormativo {

    private final LocalDate dataDeReferencia;
    private final RepositorioClassificacaoTributaria classificacoes;
    private final RepositorioNcm ncms;
    private final RepositorioItemAnexo itensDeAnexo;
    private final RepositorioAliquota aliquotas;

    public ContextoNormativoNaData(
            LocalDate dataDeReferencia,
            RepositorioClassificacaoTributaria classificacoes,
            RepositorioNcm ncms,
            RepositorioItemAnexo itensDeAnexo,
            RepositorioAliquota aliquotas) {

        this.dataDeReferencia = exigir(dataDeReferencia,
                "O contexto normativo precisa da data do documento: sem ela não há o que resolver.");
        this.classificacoes = exigir(classificacoes, "Repositório de classificações tributárias não informado.");
        this.ncms = exigir(ncms, "Repositório de NCM não informado.");
        this.itensDeAnexo = exigir(itensDeAnexo, "Repositório de itens de anexo não informado.");
        this.aliquotas = exigir(aliquotas, "Repositório de alíquotas não informado.");
    }

    @Override
    public Optional<ClassificacaoTributaria> classificacaoTributaria(CodigoClassificacaoTributaria codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return classificacoes.buscarVigenteEm(codigo, dataDeReferencia);
    }

    @Override
    public Optional<RegistroNcm> registroNcm(Ncm ncm) {
        if (ncm == null) {
            return Optional.empty();
        }
        return ncms.buscarVigenteEm(ncm, dataDeReferencia);
    }

    @Override
    public List<ItemAnexo> anexosDoNcm(Ncm ncm) {
        if (ncm == null) {
            return List.of();
        }
        return itensDeAnexo.buscarVigentesEm(ncm, dataDeReferencia);
    }

    @Override
    public Optional<AliquotaVigente> aliquota(Tributo tributo, Abrangencia abrangencia) {
        if (tributo == null || abrangencia == null) {
            return Optional.empty();
        }
        return aliquotas.buscarVigenteEm(tributo, abrangencia, dataDeReferencia);
    }

    @Override
    public List<AliquotaVigente> aliquotas(Tributo tributo) {
        if (tributo == null) {
            return List.of();
        }
        return aliquotas.buscarVigentesEm(tributo, dataDeReferencia);
    }

    private static <T> T exigir(T valor, String mensagem) {
        if (valor == null) {
            throw new CatalogoInvalido(mensagem);
        }
        return valor;
    }
}
