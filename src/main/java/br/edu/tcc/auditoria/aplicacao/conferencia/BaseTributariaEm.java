package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Representa a base normativa carregada respondendo numa data; consulta não feita e consulta sem resposta são estados diferentes.
public record BaseTributariaEm(
        LocalDate data,
        String versaoDoCatalogo,
        boolean cargaDisponivel,
        NaturezaDaCarga natureza,
        Optional<CoberturaDoCatalogo> cobertura,
        List<TratamentoDeTributo> aliquotas,
        Optional<LeituraDoCatalogo<DescricaoDeNcm>> ncmConsultado,
        Optional<LeituraDoCatalogo<EnquadramentoDoNcm>> anexosDoNcmConsultado,
        Optional<LeituraDoCatalogo<ClassificacaoDoCatalogo>> classificacaoConsultada) {

    // Texto que a tela exibe para explicar que a base é consultada por NCM e cClassTrib, e não listada inteira.
    public static final String COMO_CONSULTAR =
            "Esta tela consulta a base carregada por NCM e por cClassTrib, e mostra as alíquotas "
                    + "vigentes na data escolhida. Ela não lista as tabelas inteiras: a de NCM tem "
                    + "milhares de linhas, e o sistema guarda o catálogo para responder perguntas "
                    + "datadas, não para ser folheado.";

    // Valida a resposta: exige data, natureza e versão, e confere que cobertura e consultas concordem entre si.
    public BaseTributariaEm {
        if (data == null) {
            throw new ConferenciaInvalida(
                    "A base tributária só existe datada: sem data a resposta não significa nada.");
        }
        if (natureza == null) {
            throw new ConferenciaInvalida(
                    "A resposta precisa da procedência da carga: sem ela a tela exibiria tabela de "
                            + "demonstração sem dizer que é.");
        }
        if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()) {
            throw new ConferenciaInvalida(
                    "A resposta precisa dizer de qual carga ela veio, ainda que seja para dizer que não "
                            + "há nenhuma.");
        }
        if (cobertura == null
                || aliquotas == null
                || ncmConsultado == null
                || anexosDoNcmConsultado == null
                || classificacaoConsultada == null) {
            throw new ConferenciaInvalida(
                    "Ausência se representa com Optional.empty() e com lista vazia, nunca com nulo.");
        }
        if (cargaDisponivel == cobertura.isEmpty()) {
            throw new ConferenciaInvalida(
                    "Ou a carga está gravada e a cobertura declarada dela vem junto, ou não está e não "
                            + "vem. As duas afirmações precisam concordar.");
        }
        if (ncmConsultado.isPresent() != anexosDoNcmConsultado.isPresent()) {
            throw new ConferenciaInvalida(
                    "Consultar um NCM responde as duas coisas: o que a carga diz dele e a que anexos ela "
                            + "o vincula. Responder uma sem a outra esconderia metade do enquadramento.");
        }
        aliquotas = List.copyOf(aliquotas);
    }

    public String comoConsultar() {
        return COMO_CONSULTAR;
    }
}
