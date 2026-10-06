import { el, trocar } from '../dom.js';
import { falha } from './pecas.js';

export function abas(definicoes, escolhida, aoEscolher) {
  const lista = el('div', { role: 'tablist', classe: 'abas', 'aria-label': 'Partes do resultado' });
  const paineis = el('div', {});
  const botoes = [];
  const montadas = new Set();

  const inicial = definicoes.some((aba) => aba.chave === escolhida) ? escolhida : definicoes[0].chave;

  definicoes.forEach((aba, posicao) => {
    const botao = el('button', {
      type: 'button',
      role: 'tab',
      id: 'aba-' + aba.chave,
      classe: 'aba',
      'aria-controls': 'painel-' + aba.chave,
      texto: aba.rotulo,
    });
    const painel = el('section', {
      role: 'tabpanel',
      id: 'painel-' + aba.chave,
      'aria-labelledby': 'aba-' + aba.chave,
      classe: 'painel',
      tabindex: '0',
    });
    botao.addEventListener('click', () => ativar(aba.chave, true));
    botao.addEventListener('keydown', (evento) => {
      let destino = null;
      if (evento.key === 'ArrowRight') destino = (posicao + 1) % definicoes.length;
      if (evento.key === 'ArrowLeft') destino = (posicao - 1 + definicoes.length) % definicoes.length;
      if (evento.key === 'Home') destino = 0;
      if (evento.key === 'End') destino = definicoes.length - 1;
      if (destino !== null) {
        evento.preventDefault();
        ativar(definicoes[destino].chave, true);
        botoes[destino].focus();
      }
    });
    botoes.push(botao);
    lista.appendChild(botao);
    paineis.appendChild(painel);
  });

  async function ativar(chave, pelaPessoa) {
    definicoes.forEach((aba, posicao) => {
      const ativa = aba.chave === chave;
      botoes[posicao].setAttribute('aria-selected', ativa ? 'true' : 'false');
      botoes[posicao].setAttribute('tabindex', ativa ? '0' : '-1');
      const painel = paineis.children ? paineis.children[posicao] : null;
      if (painel) {
        painel.hidden = !ativa;
      }
    });
    if (pelaPessoa && aoEscolher) {
      aoEscolher(chave);
    }
    const posicao = definicoes.findIndex((aba) => aba.chave === chave);
    const painel = paineis.children[posicao];
    if (!montadas.has(chave)) {
      montadas.add(chave);
      trocar(painel, [el('p', { classe: 'carregando', role: 'status', texto: 'Carregando...' })]);
      try {
        await definicoes[posicao].desenhar(painel);
      } catch (erro) {
        trocar(painel, [falha(erro)]);
      }
    }
  }

  const raiz = el('div', { classe: 'bloco-de-abas' }, [lista, paineis]);
  raiz.pronto = ativar(inicial, false);
  return raiz;
}
