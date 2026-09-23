package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// Representa a linha de um arquivo que uma execução não conseguiu ler. O nome já chega sem a pasta e sem o CNPJ de dentro da chave de acesso, limpo por OrigemDeArquivoIlegivel, e a V7 também restringe a coluna.
@Entity
@Table(name = "falha_de_leitura_da_execucao")
class FalhaDeLeituraDaExecucaoEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "execucao_id", nullable = false)
    private UUID execucaoId;

    // Ordem em que a falha aconteceu, para o relatório sair sempre igual.
    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "origem", nullable = false)
    private String origem;

    @Column(name = "tipo_de_erro", nullable = false)
    private String tipoDeErro;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    // Construtor vazio exigido pelo JPA.
    protected FalhaDeLeituraDaExecucaoEntidade() {
    }

    // Construtor que recebe todos os campos da linha.
    FalhaDeLeituraDaExecucaoEntidade(
            UUID id, UUID execucaoId, int ordem, String origem, String tipoDeErro, String motivo) {
        this.id = id;
        this.execucaoId = execucaoId;
        this.ordem = ordem;
        this.origem = origem;
        this.tipoDeErro = tipoDeErro;
        this.motivo = motivo;
    }

    String origem() {
        return origem;
    }

    String tipoDeErro() {
        return tipoDeErro;
    }

    String motivo() {
        return motivo;
    }
}
