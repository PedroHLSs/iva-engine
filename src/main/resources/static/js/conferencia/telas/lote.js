import { el, trocar } from '../../dom.js';
import { inteiro } from '../../formato.js';
import * as api from '../api.js';
import { selo, valorOuMotivo } from '../pecas.js';
import { enderecoDoGrupo } from '../roteador.js';
import { recolhivel } from '../../colapso.js';

export async function painelDeAgrupamentos(painel, analiseId, ordem) {
  trocar(painel, [el('p', { classe: 'carregando', role: 'status', texto: 'Agrupando...' })]);
  const agrupado = await api.grupos(analiseId, ordem);
  trocar(painel, [
    el('h2', { texto: 'Grupos por parametrizacao' }),
    seletorDeOrdem(agrupado, (escolhida) => painelDeAgrupamentos(painel, analiseId, escolhida)),
    el('p', { classe: 'nota significado-da-ordem', texto: agrupado.ordem.significado }),
    listaDeGrupos(agrupado.grupos, analiseId),
  ]);
}

function seletorDeOrdem(agrupado, aoEscolher) {
  const botoes = agrupado.ordensDisponiveis.map((disponivel) => el('button', {
    classe: 'botao' + (disponivel.ordem === agrupado.ordem.ordem ? ' principal' : ''),
    'aria-pressed': disponivel.ordem === agrupado.ordem.ordem ? 'true' : 'false',
    title: disponivel.significado,
    texto: disponivel.rotulo,
    aoClicar: () => aoEscolher(disponivel.ordem),
  }));
  return el('div', { classe: 'acoes' }, [
    el('span', { classe: 'nota', texto: 'ordenar por:' }),
    ...botoes,
  ]);
}

function listaDeGrupos(grupos, analiseId) {
  if (!grupos.length) {
    return el('p', { classe: 'tabela-vazia', texto: 'Nenhum produto foi lido neste lote.' });
  }

  return el('div', { classe: 'grupos' }, grupos.map((grupo) => recolhivel(
    el('span', { classe: 'grupo-chave' }, [
      selo(grupo.situacao, grupo.rotuloDaSituacao, grupo.explicacaoDaSituacao),
      el('span', { classe: 'grupo-rotulo', texto: 'NCM' }),
      valorOuMotivo(grupo.ncm, grupo.motivoDoNcmAusente),
      el('span', { classe: 'grupo-rotulo', texto: 'cClassTrib' }),
      valorOuMotivo(grupo.cClassTrib, grupo.motivoDoClassTribAusente),
    ]),
    [

    el('p', { classe: 'grupo-nivel', texto: grupo.rotuloDoNivel }),

    el('div', { classe: 'grupo-medida' }, [
      el('div', {}, [
        el('strong', { texto: inteiro(grupo.quantidadeDeProdutos) }),
        el('span', { texto: ' produto(s) em ' + inteiro(grupo.quantidadeDeNotas) + ' nota(s)' }),
      ]),
      el('div', {}, [
        el('strong', { classe: 'mono', texto: grupo.valorDosProdutos }),
        el('span', { classe: 'rotulo-do-valor', texto: ' ' + grupo.rotuloDoValorDosProdutos }),
      ]),
    ]),

    contagensDoGrupo(grupo),

    el('div', { classe: 'acoes' }, [
      el('a', {
        classe: 'botao',
        href: enderecoDoGrupo(analiseId, grupo),
        texto: 'ver as notas e os itens',
      }),
    ]),
    ],
    grupo.recolhidoPorPadrao,
    'grupo-da-conferencia',
  )));
}

function contagensDoGrupo(grupo) {
  const partes = grupo.verificacoesPorEstado.map((contagem) => el('span', {
    classe: 'contagem-estado',
  }, [
    el('strong', { texto: inteiro(contagem.quantidade) }),
    ' ' + contagem.rotulo,
  ]));

  return el('div', { classe: 'grupo-corpo' }, [
    el('p', { classe: 'nota', texto: 'verificacoes deste grupo:' }),
    el('div', { classe: 'contagens-inline' }, partes),
    el('p', {
      classe: 'nota',
      texto: inteiro(grupo.produtosComAlgumaVerificacaoNaoConcluida)
        + ' produto(s) deste grupo tem ao menos uma verificacao sem conclusao.',
    }),
  ]);
}
