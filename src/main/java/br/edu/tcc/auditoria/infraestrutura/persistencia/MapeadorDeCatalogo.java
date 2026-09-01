package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tradução entre os registros do catálogo no domínio e as linhas gravadas.
 *
 * <p>Existe porque o domínio não tem anotação de JPA e não vai ter (D001).
 * O preço é este arquivo; o retorno é que trocar de banco, de ORM ou de esquema
 * não toca em nenhuma regra de negócio.</p>
 *
 * <p>A tradução é literal: nada é convertido, arredondado, normalizado ou
 * completado no caminho. Um percentual gravado com três casas volta com três
 * casas, e vigência sem fim volta como {@code Optional.empty()}, nunca como uma
 * data escolhida por conveniência.</p>
 */
final class MapeadorDeCatalogo {

    private MapeadorDeCatalogo() {
    }

    static ClassificacaoTributariaEntidade paraEntidade(ClassificacaoTributaria registro, UUID cargaId) {
        Set<String> csts = new LinkedHashSet<>();
        registro.cstsCompativeis().forEach(cst -> csts.add(cst.valor()));

        return new ClassificacaoTributariaEntidade(
                UUID.randomUUID(),
                cargaId,
                registro.codigo().valor(),
                registro.dispositivoLegal(),
                registro.indicadorDeBeneficio(),
                registro.percentualReducao().orElse(null),
                registro.vigenciaInicio(),
                registro.vigenciaFim().orElse(null),
                registro.fonteNormativa(),
                csts,
                registro.camposObrigatoriosCondicionados());
    }

    static ClassificacaoTributaria paraDominio(ClassificacaoTributariaEntidade entidade) {
        Set<CodigoCst> csts = new LinkedHashSet<>();
        entidade.cstsCompativeis().forEach(cst -> csts.add(new CodigoCst(cst)));

        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(entidade.codigo()),
                csts,
                entidade.dispositivoLegal(),
                entidade.indicadorDeBeneficio(),
                Optional.ofNullable(entidade.percentualReducao()),
                entidade.camposObrigatoriosCondicionados(),
                procedencia(entidade.vigenciaInicio(), entidade.vigenciaFim(), entidade.fonteNormativa()));
    }

    static RegistroNcmEntidade paraEntidade(RegistroNcm registro, UUID cargaId) {
        return new RegistroNcmEntidade(
                UUID.randomUUID(),
                cargaId,
                registro.ncm().valor(),
                registro.descricao(),
                registro.vigenciaInicio(),
                registro.vigenciaFim().orElse(null),
                registro.fonteNormativa());
    }

    static RegistroNcm paraDominio(RegistroNcmEntidade entidade) {
        return new RegistroNcm(
                new Ncm(entidade.ncm()),
                entidade.descricao(),
                procedencia(entidade.vigenciaInicio(), entidade.vigenciaFim(), entidade.fonteNormativa()));
    }

    static ItemAnexoEntidade paraEntidade(ItemAnexo registro, UUID cargaId) {
        return new ItemAnexoEntidade(
                UUID.randomUUID(),
                cargaId,
                registro.ncm().valor(),
                registro.identificadorDoAnexo().valor(),
                registro.tipoDeTratamento(),
                registro.vigenciaInicio(),
                registro.vigenciaFim().orElse(null),
                registro.fonteNormativa());
    }

    static ItemAnexo paraDominio(ItemAnexoEntidade entidade) {
        return new ItemAnexo(
                new Ncm(entidade.ncm()),
                new IdentificadorAnexo(entidade.identificadorAnexo()),
                entidade.tipoDeTratamento(),
                procedencia(entidade.vigenciaInicio(), entidade.vigenciaFim(), entidade.fonteNormativa()));
    }

    static AliquotaVigenteEntidade paraEntidade(AliquotaVigente registro, UUID cargaId) {
        return new AliquotaVigenteEntidade(
                UUID.randomUUID(),
                cargaId,
                registro.tributo(),
                registro.percentual(),
                registro.abrangencia().valor(),
                registro.vigenciaInicio(),
                registro.vigenciaFim().orElse(null),
                registro.fonteNormativa());
    }

    static AliquotaVigente paraDominio(AliquotaVigenteEntidade entidade) {
        return new AliquotaVigente(
                entidade.tributo(),
                entidade.percentual(),
                new Abrangencia(entidade.abrangencia()),
                procedencia(entidade.vigenciaInicio(), entidade.vigenciaFim(), entidade.fonteNormativa()));
    }

    static ProcedenciaNormativa procedencia(LocalDate inicio, LocalDate fim, String fonteNormativa) {
        return new ProcedenciaNormativa(
                new PeriodoVigencia(inicio, Optional.ofNullable(fim)), fonteNormativa);
    }
}
