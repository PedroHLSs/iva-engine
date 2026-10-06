package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.IncidenciaDaReducao;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Classe que converte os registros do catálogo entre o domínio e as linhas gravadas, porque o domínio não tem anotação de JPA. A conversão é literal: nada é arredondado nem completado, e vigência sem fim volta como Optional vazio.
// Emenda de 03/10/2026 (D015): os campos exigidos passaram a gravar também se a carga os declarou (coluna da V17), porque a tabela filha sozinha não separa "não declarado" de NENHUM.
// Emenda de 03/10/2026 (D017, V19): grava e lê a incidência da redução, null quando a carga não a declarou.
final class MapeadorDeCatalogo {

    // Como a coluna anexos_admitidos grava a declaração de que o código não exige anexo (V15).
    private static final String NENHUM_ANEXO = "NENHUM";

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private MapeadorDeCatalogo() {
    }

    // Método estático que converte uma classificação tributária para gravar.
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
                registro.reducaoIncideSobre().map(IncidenciaDaReducao::name).orElse(null),
                registro.tributacaoIntegral().orElse(null),
                registro.anexosAdmitidos().map(MapeadorDeCatalogo::anexosParaColuna).orElse(null),
                registro.vigenciaInicio(),
                registro.vigenciaFim().orElse(null),
                registro.fonteNormativa(),
                csts,
                registro.camposObrigatoriosCondicionados().isPresent(),
                registro.camposObrigatoriosCondicionados().orElse(List.of()));
    }

    // Método estático que remonta a classificação tributária do domínio.
    static ClassificacaoTributaria paraDominio(ClassificacaoTributariaEntidade entidade) {
        Set<CodigoCst> csts = new LinkedHashSet<>();
        entidade.cstsCompativeis().forEach(cst -> csts.add(new CodigoCst(cst)));

        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(entidade.codigo()),
                csts,
                entidade.dispositivoLegal(),
                entidade.indicadorDeBeneficio(),
                Optional.ofNullable(entidade.percentualReducao()),
                Optional.ofNullable(entidade.reducaoIncideSobre()).map(IncidenciaDaReducao::valueOf),
                Optional.ofNullable(entidade.tributacaoIntegral()),
                Optional.ofNullable(entidade.anexosAdmitidos()).map(MapeadorDeCatalogo::anexosDaColuna),
                camposDaEntidade(entidade),
                procedencia(entidade.vigenciaInicio(), entidade.vigenciaFim(), entidade.fonteNormativa()));
    }

    // Método auxiliar que remonta os campos exigidos (D015): a coluna diz se a carga declarou. Nula é carga anterior à V17, e nela só uma lista com nomes é declaração; lista vazia de carga antiga fica "não declarado", porque ali branco e NENHUM eram a mesma coisa.
    private static Optional<List<String>> camposDaEntidade(ClassificacaoTributariaEntidade entidade) {
        List<String> campos = List.copyOf(entidade.camposObrigatoriosCondicionados());
        if (Boolean.TRUE.equals(entidade.camposObrigatoriosDeclarados()) || !campos.isEmpty()) {
            return Optional.of(campos);
        }
        return Optional.empty();
    }

    // Método auxiliar que escreve os anexos admitidos na coluna: NENHUM para o conjunto vazio, ou os identificadores em ordem, separados por |.
    private static String anexosParaColuna(Set<IdentificadorAnexo> anexos) {
        if (anexos.isEmpty()) {
            return NENHUM_ANEXO;
        }
        return String.join("|", anexos.stream().map(IdentificadorAnexo::valor).sorted().toList());
    }

    // Método auxiliar que lê os anexos admitidos da coluna, no caminho inverso de anexosParaColuna.
    private static Set<IdentificadorAnexo> anexosDaColuna(String coluna) {
        if (coluna.equals(NENHUM_ANEXO)) {
            return Set.of();
        }
        Set<IdentificadorAnexo> anexos = new LinkedHashSet<>();
        Arrays.stream(coluna.split("\\|")).map(IdentificadorAnexo::new).forEach(anexos::add);
        return anexos;
    }

    // Método estático que converte um registro de NCM para gravar.
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

    // Método estático que remonta o registro de NCM do domínio.
    static RegistroNcm paraDominio(RegistroNcmEntidade entidade) {
        return new RegistroNcm(
                new Ncm(entidade.ncm()),
                entidade.descricao(),
                procedencia(entidade.vigenciaInicio(), entidade.vigenciaFim(), entidade.fonteNormativa()));
    }

    // Método estático que converte um vínculo de NCM e anexo para gravar.
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

    // Método estático que remonta o vínculo de NCM e anexo do domínio.
    static ItemAnexo paraDominio(ItemAnexoEntidade entidade) {
        return new ItemAnexo(
                new Ncm(entidade.ncm()),
                new IdentificadorAnexo(entidade.identificadorAnexo()),
                entidade.tipoDeTratamento(),
                procedencia(entidade.vigenciaInicio(), entidade.vigenciaFim(), entidade.fonteNormativa()));
    }

    // Método estático que converte uma alíquota para gravar.
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

    // Método estático que remonta a alíquota do domínio.
    static AliquotaVigente paraDominio(AliquotaVigenteEntidade entidade) {
        return new AliquotaVigente(
                entidade.tributo(),
                entidade.percentual(),
                new Abrangencia(entidade.abrangencia()),
                procedencia(entidade.vigenciaInicio(), entidade.vigenciaFim(), entidade.fonteNormativa()));
    }

    // Método estático que monta a procedência com o período e a fonte.
    static ProcedenciaNormativa procedencia(LocalDate inicio, LocalDate fim, String fonteNormativa) {
        return new ProcedenciaNormativa(
                new PeriodoVigencia(inicio, Optional.ofNullable(fim)), fonteNormativa);
    }
}
