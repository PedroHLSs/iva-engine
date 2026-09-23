package br.edu.tcc.auditoria.infraestrutura.api;

// Classe que guarda o aviso de uso que vai em toda resposta de resultado. Ele vem do servidor, e não do JavaScript, para nenhuma tela esquecer de mostrá-lo.
final class AvisoDeUso {

    static final String TEXTO =
            "Esta análise apoia a conferência fiscal. Ela se baseia nos dados do documento enviado e "
                    + "na base normativa cadastrada, alcança apenas o que as regras cadastradas "
                    + "examinam, não substitui a avaliação de profissional tributário e não constitui "
                    + "parecer.";

    // Construtor privado: ninguém cria objeto desta classe, só usa o texto.
    private AvisoDeUso() {
    }
}
