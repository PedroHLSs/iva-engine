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

// Controlador que responde GET /api/base-tributaria: consulta a base normativa carregada numa data que precisa ser informada. Não existe "hoje" como padrão, para a resposta não mudar sozinha de um dia para o outro, e a consulta é por NCM e cClassTrib, sem listar as tabelas inteiras.
@RestController
@RequestMapping("/api/base-tributaria")
class ControladorDaBaseTributaria {

    private final ConsultaDaBaseTributaria base;

    // Construtor que recebe a consulta da base tributária.
    ControladorDaBaseTributaria(ConsultaDaBaseTributaria base) {
        this.base = base;
    }

    // Consulta a base na data pedida; NCM e cClassTrib são opcionais, e o formato deles é conferido pelo domínio.
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
