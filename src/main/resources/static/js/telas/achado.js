/* ---------------------------------------------------------------------------
   Tela 4 - Detalhe do achado.

   COMO A TELA ACHA O APONTAMENTO

   A API nao tem GET /api/achados/{id}: os apontamentos vem paginados dentro da
   execucao. Entao o detalhe recarrega as paginas e localiza o identificador.
   Custa uma volta a mais e mantem o endereco compartilhavel - alguem pode colar
   o link do achado para um colega e ele abre.

   AS DUAS AUSENCIAS DA EVIDENCIA NAO SAO A MESMA COISA

   valorEncontrado vazio e campo que o contribuinte nao declarou. valorEsperado
   vazio e regra sem valor de referencia a opor. As duas viriam em branco numa
   tabela comum, e a tela escreve as duas, cada uma com a sua frase.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../dom.js';
import {
  inteiro, severidade, data, dataHora, ausente, documentoCurto, rotuloDaRegra,
} from '../formato.js';
import { comoQuantia } from '../decimal.js';
import { navegacaoDaExecucao, falha } from '../comum.js';
import { endereco } from '../roteador.js';
import * as api from '../api.js';

export async function desenhar(tela, parametros, rota) {
  const voltar = el('a', {
    classe: 'voltar', href: endereco('achados', rota.execucaoId), texto: 'Achados',
  });
  const progresso = el('p', { classe: 'carregando', texto: 'Procurando o apontamento...' });
  trocar(tela, [voltar, progresso]);

  let carregado;
  try {
    carregado = await api.todasAsPaginas(
      (pagina, tamanho) => api.paginaDeAchados(rota.execucaoId, {}, pagina, tamanho),
      (feitas, total) => {
        progresso.textContent = 'Procurando o apontamento - pagina ' + feitas + ' de ' + total
          + '.';
      },
    );
  } catch (erro) {
    trocar(tela, [voltar, falha(erro)]);
    return;
  }

  const achado = carregado.linhas.find((linha) => linha.id === rota.achadoId);
  if (!achado) {
    trocar(tela, [voltar, el('div', { classe: 'aviso erro' }, [
      el('strong', { texto: 'Apontamento nao encontrado nesta execucao. ' }),
      'O identificador ' + rota.achadoId + ' nao esta entre os '
        + inteiro(carregado.linhas.length) + ' apontamento(s) gravados aqui.',
    ])]);
    return;
  }

  trocar(tela, [
    voltar,
    navegacaoDaExecucao(rota.execucaoId, 'achados'),
    el('div', { classe: 'cabecalho-achado' }, [
      rotuloDaRegra(achado),
      el('span', { classe: 'chip chip-neutro', texto: 'versao ' + achado.regraVersao }),
      severidade(achado.severidade),
      el('span', { classe: 'chip chip-achado', texto: achado.resultado }),
      el('span', { classe: 'chip chip-neutro', texto: 'tratativa ' + achado.statusDeTratativa }),
    ]),
    documento(achado.documento, achado.numeroItem),
    el('h3', { texto: 'evidencia, campo a campo' }),
    evidencias(achado.evidencias),
    el('div', { classe: 'bloco' }, [
      el('h3', { texto: 'valor em risco' }),
      achado.valorEmRisco !== null
        ? el('p', { classe: 'mono', texto: comoQuantia(achado.valorEmRisco) })
        : el('p', {}, [ausente(achado.motivoDoValorAusente)]),
    ]),
    el('div', { classe: 'bloco' }, [
      el('h3', { texto: 'fundamento normativo' }),
      el('p', { classe: 'texto-normativo', texto: achado.fundamentoNormativo }),
    ]),
    el('div', { classe: 'bloco' }, [
      el('h3', { texto: 'vigencia aplicada' }),
      vigencia(achado.vigenciaAplicada),
    ]),
    el('div', { classe: 'bloco' }, [
      el('h3', { texto: 'tratativa' }),
      tratativa(achado),
    ]),
    el('p', {
      classe: 'nota',
      texto: 'Detectado em ' + dataHora(achado.detectadoEm) + '; visto pela ultima vez em '
        + dataHora(achado.vistoEm) + '. Reprocessar o mesmo lote nao cria apontamento novo nem '
        + 'apaga a decisao ja registrada.',
    }),
  ]);
}

function documento(doc, numeroItem) {
  return el('section', { classe: 'cartao' }, [
    el('dl', { classe: 'pares' }, [
      el('dt', { texto: 'pseudonimo' }),
      el('dd', { classe: 'mono', texto: doc.pseudonimo }),
      el('dt', { texto: 'chave de acesso' }),
      el('dd', {}, [
        doc.chaveAcesso !== null
          ? el('span', { classe: 'mono', texto: doc.chaveAcesso })
          : ausente(doc.motivoDaChaveOmitida),
      ]),
      el('dt', { texto: 'nota' }),
      el('dd', { texto: documentoCurto(doc) + ', item ' + numeroItem }),
      el('dt', { texto: 'emissao' }),
      el('dd', { texto: data(doc.dataEmissao) + ' - UF ' + (doc.ufEmitente || '') }),
    ]),
  ]);
}

/** De onde a evidencia veio, por extenso. */
function origem(o) {
  if (o.tipo === 'DO_DOCUMENTO') {
    return 'do documento' + (o.localizacao ? ' (' + o.localizacao + ')' : '');
  }
  if (o.tipo === 'DE_TABELA_NORMATIVA') {
    const partes = [];
    if (o.nomeTabela) {
      partes.push('tabela ' + o.nomeTabela);
    }
    if (o.versaoTabela) {
      partes.push(o.versaoTabela);
    }
    return 'de tabela normativa' + (partes.length ? ': ' + partes.join(' - ') : '');
  }
  if (o.tipo === 'DA_REGRA') {
    return 'da regra' + (o.descricao ? ': ' + o.descricao : '');
  }
  return o.tipo;
}

function evidencias(lista) {
  const linhas = lista.map((evidencia) => el('tr', {}, [
    el('td', { classe: 'mono', texto: evidencia.campoAnalisado }),
    el('td', {}, [
      evidencia.valorEncontrado !== null
        ? el('span', { classe: 'mono', texto: evidencia.valorEncontrado })
        : ausente('campo nao informado no documento'),
    ]),
    el('td', {}, [
      evidencia.valorEsperado !== null
        ? el('span', { classe: 'mono', texto: evidencia.valorEsperado })
        : ausente('a regra nao tinha valor de referencia a opor'),
    ]),
    el('td', { texto: origem(evidencia.origem) }),
  ]));

  return el('div', { classe: 'rolagem' }, [
    el('table', {}, [
      el('caption', {
        texto: 'Encontrado em branco e campo que o contribuinte nao declarou; esperado em branco '
          + 'e regra sem valor de referencia a opor. Sao ausencias de natureza diferente, e '
          + 'nenhuma das duas e zero.',
      }),
      el('thead', {}, [
        el('tr', {}, [
          el('th', { texto: 'campo analisado' }),
          el('th', { texto: 'valor encontrado' }),
          el('th', { texto: 'valor esperado' }),
          el('th', { texto: 'origem' }),
        ]),
      ]),
      el('tbody', {}, linhas),
    ]),
  ]);
}

function vigencia(v) {
  return el('dl', { classe: 'pares' }, [
    el('dt', { texto: 'inicio' }),
    el('dd', { texto: data(v.inicio) }),
    el('dt', { texto: 'fim' }),
    el('dd', {}, [
      v.fim !== null
        ? document.createTextNode(data(v.fim))
        : ausente(v.motivoDoFimAusente),
    ]),
  ]);
}

function tratativa(achado) {
  if (achado.tratativa === null) {
    return el('p', {
      classe: 'nota',
      texto: 'Sem decisao registrada: o apontamento esta ' + achado.statusDeTratativa
        + '. Registrar tratativa e ato de uma pessoa identificada, e continua na CLI.',
    });
  }
  const t = achado.tratativa;
  return el('dl', { classe: 'pares' }, [
    el('dt', { texto: 'decisao' }),
    el('dd', { texto: t.decisao }),
    el('dt', { texto: 'registrada em' }),
    el('dd', { texto: dataHora(t.registradoEm) }),
    el('dt', { texto: 'justificativa' }),
    el('dd', {}, [
      t.justificativa !== null
        ? document.createTextNode(t.justificativa)
        : ausente(t.motivoDaJustificativaOmitida),
    ]),
  ]);
}
