package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.TipoDeCodigoDoAnexo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Representa um anexo que uma carga declarou (V16, 01/10/2026). Início null quer dizer que o anexo existe mas não está carregado; fim null com início é vigência aberta.
@Entity
@Table(name = "anexo_declarado")
@IdClass(AnexoDeclaradoEntidade.Chave.class)
class AnexoDeclaradoEntidade {

    @Id
    @Column(name = "carga_id", nullable = false)
    private UUID cargaId;

    @Id
    @Column(name = "identificador", nullable = false)
    private String identificador;

    @Column(name = "tipo_de_codigo", nullable = false)
    private String tipoDeCodigo;

    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "fonte_normativa", nullable = false)
    private String fonteNormativa;

    // Construtor vazio exigido pelo JPA.
    protected AnexoDeclaradoEntidade() {
    }

    // Construtor que recebe todos os campos da linha.
    AnexoDeclaradoEntidade(UUID cargaId, String identificador, String tipoDeCodigo,
            LocalDate vigenciaInicio, LocalDate vigenciaFim, String fonteNormativa) {
        this.cargaId = cargaId;
        this.identificador = identificador;
        this.tipoDeCodigo = tipoDeCodigo;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.fonteNormativa = fonteNormativa;
    }

    // Método estático que converte o anexo declarado do domínio para gravar.
    static AnexoDeclaradoEntidade de(AnexoDeclarado anexo, UUID cargaId) {
        return new AnexoDeclaradoEntidade(
                cargaId,
                anexo.identificador().valor(),
                anexo.tipoDeCodigo().name(),
                anexo.carregamento().map(PeriodoVigencia::inicio).orElse(null),
                anexo.carregamento().flatMap(PeriodoVigencia::fim).orElse(null),
                anexo.fonteNormativa());
    }

    // Remonta o anexo declarado do domínio, sem completar nada.
    AnexoDeclarado paraDominio() {
        Optional<PeriodoVigencia> carregamento = Optional.ofNullable(vigenciaInicio)
                .map(inicio -> vigenciaFim == null
                        ? PeriodoVigencia.aPartirDe(inicio)
                        : PeriodoVigencia.de(inicio, vigenciaFim));
        return new AnexoDeclarado(new IdentificadorAnexo(identificador),
                TipoDeCodigoDoAnexo.valueOf(tipoDeCodigo), carregamento, fonteNormativa);
    }

    UUID cargaId() {
        return cargaId;
    }

    // Chave composta: a carga e o identificador do anexo.
    static class Chave implements Serializable {

        private UUID cargaId;
        private String identificador;

        // Construtor vazio exigido pelo JPA.
        Chave() {
        }

        // Construtor que recebe a carga e o identificador.
        Chave(UUID cargaId, String identificador) {
            this.cargaId = cargaId;
            this.identificador = identificador;
        }

        // Compara duas chaves pela carga e pelo identificador.
        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Chave chave)) {
                return false;
            }
            return Objects.equals(cargaId, chave.cargaId) && Objects.equals(identificador, chave.identificador);
        }

        // Calcula o hash pela carga e pelo identificador.
        @Override
        public int hashCode() {
            return Objects.hash(cargaId, identificador);
        }
    }
}
