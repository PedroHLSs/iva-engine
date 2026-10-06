package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// Representa a marca da V20 (D018): a lista de ilegíveis desta execução foi gravada, com tantas linhas. Sem a marca, a leitura não foi registrada, o que é diferente de nenhum arquivo ter falhado.
@Entity
@Table(name = "leitura_da_execucao")
class LeituraDaExecucaoEntidade {

    @Id
    @Column(name = "execucao_id", nullable = false)
    private UUID execucaoId;

    @Column(name = "arquivos_ilegiveis", nullable = false)
    private int arquivosIlegiveis;

    // V21 (D019): documentos repetidos, com o mesmo conteúdo, descartados do lote; null em linha anterior à V21.
    @Column(name = "documentos_duplicados")
    private Integer documentosDuplicados;

    // Construtor vazio exigido pelo JPA.
    protected LeituraDaExecucaoEntidade() {
    }

    // Construtor que recebe a execução, quantos arquivos ela não conseguiu ler e quantos documentos repetidos descartou.
    LeituraDaExecucaoEntidade(UUID execucaoId, int arquivosIlegiveis, int documentosDuplicados) {
        this.execucaoId = execucaoId;
        this.arquivosIlegiveis = arquivosIlegiveis;
        this.documentosDuplicados = documentosDuplicados;
    }

    int arquivosIlegiveis() {
        return arquivosIlegiveis;
    }

    Integer documentosDuplicados() {
        return documentosDuplicados;
    }
}
