package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.util.List;
import java.util.Optional;

// Interface que representa o catálogo já resolvido na data do documento; nenhum método aceita data, para que a regra não escolha em que data consultar, e vazio nunca é conformidade.
public interface ContextoNormativo {

    // Retorna o que o catálogo diz sobre o cClassTrib, na data de referência.
    Optional<ClassificacaoTributaria> classificacaoTributaria(CodigoClassificacaoTributaria codigo);

    // Retorna o que o catálogo diz sobre o NCM, na data de referência.
    Optional<RegistroNcm> registroNcm(Ncm ncm);

    // Retorna os anexos a que o NCM estava vinculado na data de referência.
    List<ItemAnexo> anexosDoNcm(Ncm ncm);

    // Retorna a alíquota do par tributo e abrangência, na data de referência.
    Optional<AliquotaVigente> aliquota(Tributo tributo, Abrangencia abrangencia);

    // Retorna as alíquotas do tributo em todas as abrangências, na data de referência.
    List<AliquotaVigente> aliquotas(Tributo tributo);
}
