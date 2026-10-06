import { el } from './dom.js';
import { inteiro } from './formato.js';

let sequenciaDeInfo = 0;

export function botaoDeInformacao(rotulo, conteudo) {
  sequenciaDeInfo += 1;
  const id = 'info-painel-' + sequenciaDeInfo;
  const balao = el('div', { classe: 'balao-info', id, hidden: 'hidden' }, [].concat(conteudo));
  const botao = el('button', {
    type: 'button', classe: 'botao-info', texto: 'i',
    'aria-label': rotulo, 'aria-expanded': 'false', 'aria-controls': id,
  });
  const mostrar = (aberto) => {
    balao.hidden = !aberto;
    botao.setAttribute('aria-expanded', String(aberto));
  };
  botao.addEventListener('click', () => mostrar(balao.hidden));
  botao.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') {
      mostrar(false);
    }
  });
  return { botao, balao };
}

export function barraPlana({ achado, naoAvaliado, conforme }) {
  const faixas = [
    { classe: 'f-achado', marca: '●', rotulo: 'apontamentos', valor: achado },
    { classe: 'f-naoavaliado', marca: '▲', rotulo: 'nao avaliados', valor: naoAvaliado },
    { classe: 'f-conforme', marca: '■', rotulo: 'conformes', valor: conforme },
  ];
  const escrito = (faixa) => (faixa.valor === null ? 'nao derivavel' : inteiro(faixa.valor));

  const barra = el('div', {
    classe: 'barra-plana', role: 'img',
    'aria-label': faixas.map((faixa) => faixa.rotulo + ' ' + escrito(faixa)).join(', '),
  }, faixas.filter((faixa) => faixa.valor !== null && faixa.valor > 0).map((faixa) => {
    const fatia = el('span', { classe: 'fatia ' + faixa.classe }, [
      el('span', { classe: 'fatia-marca', 'aria-hidden': 'true', texto: faixa.marca }),
    ]);
    fatia.style.flexGrow = String(faixa.valor);
    return fatia;
  }));

  const legenda = el('ul', { classe: 'legenda-plana', 'aria-hidden': 'true' }, faixas.map((faixa) => el('li', {}, [
    el('span', {
      classe: 'legenda-marca ' + faixa.classe + (faixa.valor === null ? ' sem-valor' : ''),
      texto: faixa.marca,
    }),
    faixa.rotulo + ': ' + escrito(faixa),
  ])));

  return el('div', { classe: 'barra-dos-desfechos' }, [barra, legenda]);
}
