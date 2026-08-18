package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de mentira, montado dentro do teste.
 *
 * <p>Representa o catálogo <em>já resolvido</em> numa data, que é o que
 * {@link ContextoNormativo} promete às regras. Por isso não filtra nada por
 * vigência: o que estiver aqui é o que valia na data do documento, e o que não
 * estiver é silêncio do catálogo. A resolução por vigência tem teste próprio na
 * Etapa 2 e não precisa ser reproduzida aqui.</p>
 *
 * <p>Nasce vazio de propósito. Vazio é o cenário mais importante dos testes de
 * regra — é nele que se verifica que falta de dado não vira conformidade.</p>
 */
public final class ContextoNormativoFalso implements ContextoNormativo {

    private final Map<String, ClassificacaoTributaria> classificacoes = new LinkedHashMap<>();
    private final Map<String, RegistroNcm> registrosDeNcm = new LinkedHashMap<>();
    private final Map<String, List<ItemAnexo>> anexosPorNcm = new LinkedHashMap<>();
    private final List<AliquotaVigente> aliquotas = new ArrayList<>();

    private ContextoNormativoFalso() {
    }

    /** Catálogo sem nenhum registro: o catálogo que nada diz. */
    public static ContextoNormativoFalso vazio() {
        return new ContextoNormativoFalso();
    }

    public ContextoNormativoFalso com(ClassificacaoTributaria classificacao) {
        classificacoes.put(classificacao.codigo().valor(), classificacao);
        return this;
    }

    public ContextoNormativoFalso com(RegistroNcm registro) {
        registrosDeNcm.put(registro.ncm().valor(), registro);
        return this;
    }

    public ContextoNormativoFalso com(ItemAnexo itemAnexo) {
        anexosPorNcm.computeIfAbsent(itemAnexo.ncm().valor(), ncm -> new ArrayList<>()).add(itemAnexo);
        return this;
    }

    public ContextoNormativoFalso com(AliquotaVigente aliquota) {
        aliquotas.add(aliquota);
        return this;
    }

    @Override
    public Optional<ClassificacaoTributaria> classificacaoTributaria(CodigoClassificacaoTributaria codigo) {
        return Optional.ofNullable(classificacoes.get(codigo.valor()));
    }

    @Override
    public Optional<RegistroNcm> registroNcm(Ncm ncm) {
        return Optional.ofNullable(registrosDeNcm.get(ncm.valor()));
    }

    @Override
    public List<ItemAnexo> anexosDoNcm(Ncm ncm) {
        return List.copyOf(anexosPorNcm.getOrDefault(ncm.valor(), List.of()));
    }

    @Override
    public Optional<AliquotaVigente> aliquota(Tributo tributo, Abrangencia abrangencia) {
        return aliquotas.stream()
                .filter(registro -> registro.tributo() == tributo && registro.abrangencia().equals(abrangencia))
                .findFirst();
    }

    @Override
    public List<AliquotaVigente> aliquotas(Tributo tributo) {
        return aliquotas.stream().filter(registro -> registro.tributo() == tributo).toList();
    }
}
