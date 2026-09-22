package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ConsultaDaBaseTributaria;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A base normativa carregada, somente leitura, numa data explícita.
 *
 * <h2>A data é obrigatória, e essa é a decisão</h2>
 *
 * <p>Não há valor padrão e não há {@code LocalDate.now()} em lugar nenhum deste
 * caminho. A D003 proibiu que uma regra escolhesse a data da consulta, e previu
 * que "o que vale hoje" seria um caso de uso próprio <em>com data explícita</em>.
 * Este é ele. Um padrão silencioso de "hoje" traria de volta, pela porta da
 * frente, exatamente o problema que a D003 fechou: a resposta mudaria sozinha de
 * um dia para o outro, e ninguém saberia a que dia a tela impressa se
 * referia.</p>
 *
 * <h2>Consulta, não despejo</h2>
 *
 * <p>{@code ncm} e {@code cClassTrib} são opcionais e consultam pontualmente. Não
 * existe listagem das tabelas inteiras, e o motivo está em
 * {@code BaseNormativa}: os repositórios do domínio expõem busca por chave, e
 * alargá-los sairia da restrição desta etapa. A resposta diz isso em
 * {@code comoConsultar}, para que a ausência de listagem se leia como decisão.</p>
 *
 * <p>NCM e {@code cClassTrib} malformados são recusados pelos objetos de valor do
 * domínio, que já sabem o formato de cada um. A fronteira não repete essa
 * validação.</p>
 */
@RestController
@RequestMapping("/api/base-tributaria")
class ControladorDaBaseTributaria {

    private final ConsultaDaBaseTributaria base;

    ControladorDaBaseTributaria(ConsultaDaBaseTributaria base) {
        this.base = base;
    }

    @GetMapping
    RespostaDaBaseTributaria consultar(
            @RequestParam(required = false) String data,
            @RequestParam(required = false) String ncm,
            @RequestParam(name = "cClassTrib", required = false) String cClassTrib) {

        LocalDate dia = Parametros.dataObrigatoria(data, "data");
        Optional<String> ncmPedido = Parametros.textoOpcional(ncm, "ncm");
        Optional<String> codigoPedido = Parametros.textoOpcional(cClassTrib, "cClassTrib");

        return RespostaDaBaseTributaria.de(
                base.em(
                        dia,
                        ncmPedido.map(Ncm::new),
                        codigoPedido.map(CodigoClassificacaoTributaria::new)),
                ncmPedido.orElse(null),
                codigoPedido.orElse(null));
    }
}
