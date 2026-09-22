/* ---------------------------------------------------------------------------
   Agrupamento de achados por (NCM + cClassTrib + regra).

   POR QUE AGRUPAR

   Erro de parametrizacao e sistematico. Oitocentas notas com o mesmo NCM e o
   mesmo cClassTrib apontadas pela mesma regra sao UM cadastro errado, nao
   oitocentos problemas. Uma lista plana de oitocentas linhas identicas nao diz
   ao contador o que fazer, e e o modo de falha numero um desta categoria de
   ferramenta.

   POR QUE A CHAVE DEGRADA, E POR QUE ISSO E RESULTADO E NAO LACUNA

   NCM e cClassTrib nao sao campos do achado: a API os expoe apenas dentro de
   "evidencias", e so quando a regra que apontou de fato os examinou. R03 e R04
   trazem os dois; R01, R02 e R07 trazem so cClassTrib; R06 traz so NCM; e R05,
   que confere valor de tributo, nao traz nenhum dos dois - as evidencias dela
   sao de base e de valor.

   Preencher o que falta seria inventar. Buscar o campo fora das evidencias
   seria afirmar sobre o item algo que o apontamento nao afirmou. Entao a chave
   degrada, e o nivel efetivamente usado sai como CAMPO do grupo, com rotulo
   escrito: quem olha um grupo sabe por qual chave ele foi formado sem consultar
   documentacao nenhuma, e nunca confunde um grupo de NCM + cClassTrib com um
   grupo que so pode ser de regra.

   AUSENCIA E ESCRITA, E O MOTIVO E OBSERVADO

   Quando um componente falta, o grupo traz o campo em null COM o campo irmao
   dizendo por que - a mesma convencao de valorEmRisco/motivoDoValorAusente e de
   chaveAcesso/motivoDaChaveOmitida na Etapa 8.

   E o motivo e OBSERVADO, nao decorado. Esta pagina nao sabe que "R05 nao
   examina NCM": ela conta quantas evidencias da regra, nesta execucao, trazem o
   campo, e escreve o que contou. Uma lista de regras e campos escrita aqui
   dentro seria conhecimento normativo em codigo de interface, e envelheceria
   sozinha na primeira regra nova.
   --------------------------------------------------------------------------- */

import { ehDecimal, somar, paraOrdenar } from './decimal.js';
import { regraEmTexto } from './formato.js';

const CAMPO_NCM = 'ncm';
const CAMPO_CLASSTRIB = 'cClassTrib';

/** Ordem de declaracao do enum Severidade, da mais grave para a menos grave. */
const ORDEM_DE_SEVERIDADE = ['CRITICA', 'GRAVE', 'MODERADA', 'INFORMATIVA'];

/**
 * Os quatro niveis possiveis de chave.
 *
 * O rotulo e frase inteira de proposito: ele vai para a tela como esta, e
 * precisa se explicar sozinho.
 */
export const NIVEIS = {
  NCM_E_CLASSTRIB: {
    codigo: 'NCM_E_CLASSTRIB',
    rotulo: 'agrupado por NCM + cClassTrib + regra',
    degradado: false,
  },
  SOMENTE_CLASSTRIB: {
    codigo: 'SOMENTE_CLASSTRIB',
    rotulo: 'agrupado por cClassTrib + regra (sem NCM)',
    degradado: true,
  },
  SOMENTE_NCM: {
    codigo: 'SOMENTE_NCM',
    rotulo: 'agrupado por NCM + regra (sem cClassTrib)',
    degradado: true,
  },
  SOMENTE_REGRA: {
    codigo: 'SOMENTE_REGRA',
    rotulo: 'agrupado somente por regra (sem NCM e sem cClassTrib)',
    degradado: true,
  },
};

/**
 * O valor que a evidencia registrou para um campo, ou null.
 *
 * Uma regra pode emitir duas evidencias com o mesmo campoAnalisado: uma do
 * documento, com o valor declarado, e outra da tabela normativa, sem valor
 * encontrado - e o caso de R06, que opoe o NCM declarado ao silencio da tabela.
 * Interessa a primeira que de fato traga valor.
 */
export function valorNaEvidencia(achado, campo) {
  const evidencias = achado.evidencias || [];
  for (const evidencia of evidencias) {
    if (evidencia.campoAnalisado === campo
        && evidencia.valorEncontrado !== null
        && evidencia.valorEncontrado !== undefined) {
      return evidencia.valorEncontrado;
    }
  }
  return null;
}

/** Quantas ocorrencias de cada regra trazem cada um dos dois campos. */
function contarDisponibilidade(achados) {
  const porRegra = new Map();
  for (const achado of achados) {
    const contagem = porRegra.get(achado.regraId) || { total: 0, comNcm: 0, comClassTrib: 0 };
    contagem.total += 1;
    if (valorNaEvidencia(achado, CAMPO_NCM) !== null) {
      contagem.comNcm += 1;
    }
    if (valorNaEvidencia(achado, CAMPO_CLASSTRIB) !== null) {
      contagem.comClassTrib += 1;
    }
    porRegra.set(achado.regraId, contagem);
  }
  return porRegra;
}

/**
 * O motivo, escrito, de um componente da chave nao existir neste grupo.
 *
 * Recebe o achado, e nao so o codigo, desde depois da Etapa 11: a frase escreve
 * a regra pelo nome, com o codigo entre parenteses.
 */
function motivoDaAusencia(achado, campo, contagem) {
  const regra = regraEmTexto(achado);
  const quantos = campo === CAMPO_NCM ? contagem.comNcm : contagem.comClassTrib;
  if (quantos === 0) {
    return 'nenhuma das ' + contagem.total + ' ocorrencia(s) de ' + regra
      + ' nesta execucao traz o campo "' + campo + '" na evidencia; a regra nao examinou esse '
      + 'campo, entao o agrupamento nao o usa';
  }
  return 'esta ocorrencia de ' + regra + ' nao trouxe "' + campo + '" na evidencia, embora '
    + quantos + ' de ' + contagem.total + ' ocorrencia(s) da mesma regra tragam';
}

function nivelDe(ncm, cClassTrib) {
  if (ncm !== null && cClassTrib !== null) {
    return NIVEIS.NCM_E_CLASSTRIB;
  }
  if (cClassTrib !== null) {
    return NIVEIS.SOMENTE_CLASSTRIB;
  }
  if (ncm !== null) {
    return NIVEIS.SOMENTE_NCM;
  }
  return NIVEIS.SOMENTE_REGRA;
}

function maisGrave(a, b) {
  return ORDEM_DE_SEVERIDADE.indexOf(a) <= ORDEM_DE_SEVERIDADE.indexOf(b) ? a : b;
}

/**
 * Agrupa os achados carregados.
 *
 * A chave e JSON.stringify([regraId, ncm, cClassTrib]), com null onde a
 * evidencia nao trouxe o campo. Serializar a tripla evita inventar um separador
 * que um NCM ou um cClassTrib pudesse conter, e mantem "ausente" distinto de
 * qualquer texto que um campo possa ter.
 *
 * @returns {{comValor: Array, semValor: Array, total: number}} os grupos
 *          ordenaveis por valor em risco e os que nao tem valor calculavel,
 *          em listas SEPARADAS. Ver a nota de ordenacao em ordenar().
 */
export function agrupar(achados) {
  const disponibilidade = contarDisponibilidade(achados);
  const grupos = new Map();

  for (const achado of achados) {
    const contagem = disponibilidade.get(achado.regraId);
    const ncm = valorNaEvidencia(achado, CAMPO_NCM);
    const cClassTrib = valorNaEvidencia(achado, CAMPO_CLASSTRIB);
    const nivel = nivelDe(ncm, cClassTrib);
    const chave = JSON.stringify([achado.regraId, ncm, cClassTrib]);

    let grupo = grupos.get(chave);
    if (!grupo) {
      grupo = {
        chave,
        regraId: achado.regraId,
        regraVersao: achado.regraVersao,

        // O nome por extenso, com o motivo quando falta, copiado do achado para
        // o cartao do grupo escrever a regra do mesmo jeito que a linha.
        regraNome: achado.regraNome || null,
        motivoDoNomeDaRegraAusente: achado.motivoDoNomeDaRegraAusente || null,

        // Exigencia 1: o nivel efetivamente usado e campo do grupo, com codigo
        // estavel para quem programa e rotulo escrito para quem le - o mesmo par
        // que ErroExposto usa em erro/mensagem.
        nivel: nivel.codigo,
        rotuloDoNivel: nivel.rotulo,
        nivelDegradado: nivel.degradado,

        // Exigencia 2: nada de nulo silencioso. Campo null SEMPRE acompanhado do
        // campo irmao que diz por que, e o motivo e observado nos dados.
        ncm,
        motivoDoNcmAusente:
          ncm === null ? motivoDaAusencia(achado, CAMPO_NCM, contagem) : null,
        cClassTrib,
        motivoDoCClassTribAusente:
          cClassTrib === null ? motivoDaAusencia(achado, CAMPO_CLASSTRIB, contagem) : null,

        severidade: achado.severidade,
        ocorrencias: 0,
        ocorrenciasComValor: 0,
        ocorrenciasSemValor: 0,
        valorSomado: null,
        derivacaoDaSoma: null,
        motivoDoValorAusente: null,
        observacaoDaSomaParcial: null,
        porTratativa: new Map(),
        documentos: new Set(),
        achados: [],
      };
      grupos.set(chave, grupo);
    }

    grupo.ocorrencias += 1;
    grupo.severidade = maisGrave(grupo.severidade, achado.severidade);
    grupo.achados.push(achado);
    grupo.documentos.add(achado.documento.pseudonimo);
    grupo.porTratativa.set(
      achado.statusDeTratativa,
      (grupo.porTratativa.get(achado.statusDeTratativa) || 0) + 1,
    );

    if (ehDecimal(achado.valorEmRisco)) {
      grupo.ocorrenciasComValor += 1;
      grupo.valorSomado = grupo.valorSomado === null
        ? achado.valorEmRisco
        : somar(grupo.valorSomado, achado.valorEmRisco);
    } else {
      grupo.ocorrenciasSemValor += 1;
    }
  }

  for (const grupo of grupos.values()) {
    concluirASoma(grupo);
  }
  return ordenar(Array.from(grupos.values()));
}

/**
 * Fecha a conta do grupo, dizendo quantas parcelas entraram.
 *
 * Soma parcial nunca e apresentada como total. Um grupo de cinco ocorrencias em
 * que duas nao tem valor calculavel soma tres, e diz que somou tres: apresentar
 * o numero como se fosse o valor do grupo inteiro subestimaria o risco em
 * silencio, que e a forma mais discreta de mentir num relatorio de auditoria.
 */
function concluirASoma(grupo) {
  if (grupo.ocorrenciasComValor === 0) {
    grupo.motivoDoValorAusente =
      'nenhuma das ' + grupo.ocorrencias + ' ocorrencia(s) deste grupo tem valor em risco '
      + 'calculavel; a regra apontou sem quantia a opor, e zero seria uma afirmacao que ela '
      + 'nao fez';
    return;
  }
  grupo.derivacaoDaSoma =
    'soma de ' + grupo.ocorrenciasComValor + ' de ' + grupo.ocorrencias + ' ocorrencia(s)';
  if (grupo.ocorrenciasSemValor > 0) {
    grupo.observacaoDaSomaParcial =
      'soma PARCIAL: ' + grupo.ocorrenciasSemValor + ' de ' + grupo.ocorrencias
      + ' ocorrencia(s) nao tem valor em risco calculavel e ficaram de fora da conta';
  }
}

/**
 * Ordena os grupos por valor em risco decrescente.
 *
 * Grupo sem valor calculavel NAO entra nesta ordenacao, e nao vai para o fim da
 * mesma lista: sai numa lista propria, que a tela mostra sob titulo proprio.
 * Empurrar para o rodape um grupo sem quantia o faria parecer o menos
 * importante, que e a leitura de "vale zero" - e nao ha valor zero ali, ha
 * ausencia de valor. Dentro da lista propria a ordem e por gravidade, que e o
 * criterio que sobra quando nao ha quantia.
 */
function ordenar(todos) {
  const comValor = todos.filter((grupo) => grupo.valorSomado !== null);
  const semValor = todos.filter((grupo) => grupo.valorSomado === null);

  comValor.sort((a, b) => {
    const diferenca = paraOrdenar(b.valorSomado) - paraOrdenar(a.valorSomado);
    if (diferenca !== 0) {
      return diferenca;
    }
    if (b.ocorrencias !== a.ocorrencias) {
      return b.ocorrencias - a.ocorrencias;
    }
    return a.regraId.localeCompare(b.regraId);
  });

  semValor.sort((a, b) => {
    const gravidade = ORDEM_DE_SEVERIDADE.indexOf(a.severidade)
      - ORDEM_DE_SEVERIDADE.indexOf(b.severidade);
    if (gravidade !== 0) {
      return gravidade;
    }
    if (b.ocorrencias !== a.ocorrencias) {
      return b.ocorrencias - a.ocorrencias;
    }
    return a.regraId.localeCompare(b.regraId);
  });

  return { comValor, semValor, total: todos.length };
}

/** Ordena achados soltos, para quando o agrupamento esta desligado. */
export function ordenarAchados(achados) {
  const comValor = achados.filter((achado) => ehDecimal(achado.valorEmRisco));
  const semValor = achados.filter((achado) => !ehDecimal(achado.valorEmRisco));
  comValor.sort((a, b) => paraOrdenar(b.valorEmRisco) - paraOrdenar(a.valorEmRisco));
  semValor.sort((a, b) => ORDEM_DE_SEVERIDADE.indexOf(a.severidade)
    - ORDEM_DE_SEVERIDADE.indexOf(b.severidade));
  return { comValor, semValor, total: achados.length };
}

/** Ordena por gravidade, para quando quem le pede a ordem da planilha. */
export function porGravidade(grupos) {
  return Array.from(grupos).sort((a, b) => {
    const gravidade = ORDEM_DE_SEVERIDADE.indexOf(a.severidade)
      - ORDEM_DE_SEVERIDADE.indexOf(b.severidade);
    return gravidade !== 0 ? gravidade : b.ocorrencias - a.ocorrencias;
  });
}

/** Ordena por numero de ocorrencias, para achar o cadastro mais repetido. */
export function porOcorrencias(grupos) {
  return Array.from(grupos).sort((a, b) => b.ocorrencias - a.ocorrencias);
}
