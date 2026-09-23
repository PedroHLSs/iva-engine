package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Classe que implementa o ContextoNormativo preso a uma data, a emissão do documento, guardada sem acessor para que ninguém a troque depois.
public final class ContextoNormativoNaData implements ContextoNormativo {

    private final LocalDate dataDeReferencia;
    private final RepositorioClassificacaoTributaria classificacoes;
    private final RepositorioNcm ncms;
    private final RepositorioItemAnexo itensDeAnexo;
    private final RepositorioAliquota aliquotas;

    // Construtor que recebe a data de referência e os quatro repositórios do catálogo.
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

    // Busca a classificação tributária vigente na data de referência; código nulo volta vazio.
    @Override
    public Optional<ClassificacaoTributaria> classificacaoTributaria(CodigoClassificacaoTributaria codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return classificacoes.buscarVigenteEm(codigo, dataDeReferencia);
    }

    // Busca o registro de NCM vigente na data de referência; NCM nulo volta vazio.
    @Override
    public Optional<RegistroNcm> registroNcm(Ncm ncm) {
        if (ncm == null) {
            return Optional.empty();
        }
        return ncms.buscarVigenteEm(ncm, dataDeReferencia);
    }

    // Busca os anexos do NCM vigentes na data de referência; NCM nulo volta lista vazia.
    @Override
    public List<ItemAnexo> anexosDoNcm(Ncm ncm) {
        if (ncm == null) {
            return List.of();
        }
        return itensDeAnexo.buscarVigentesEm(ncm, dataDeReferencia);
    }

    // Busca a alíquota do par tributo e abrangência vigente na data de referência.
    @Override
    public Optional<AliquotaVigente> aliquota(Tributo tributo, Abrangencia abrangencia) {
        if (tributo == null || abrangencia == null) {
            return Optional.empty();
        }
        return aliquotas.buscarVigenteEm(tributo, abrangencia, dataDeReferencia);
    }

    // Busca as alíquotas do tributo vigentes na data de referência, em todas as abrangências.
    @Override
    public List<AliquotaVigente> aliquotas(Tributo tributo) {
        if (tributo == null) {
            return List.of();
        }
        return aliquotas.buscarVigentesEm(tributo, dataDeReferencia);
    }

    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção com a mensagem informada.
    private static <T> T exigir(T valor, String mensagem) {
        if (valor == null) {
            throw new CatalogoInvalido(mensagem);
        }
        return valor;
    }
}
