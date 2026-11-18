// A tela pede operações ao Java. As regras de venda não são executadas aqui.
let estado = null;
let ocupado = false;
let versaoConsultaCartoes = 0;
let versaoConsultaVip = 0;
let assinaturaCheckout = null;
let paginaAtual = null;
let etapaAtual = 0;
const rotas = {loja: '/', carrinho: '/carrinho', checkout: '/checkout', vip: '/clube-vip', estoque: '/estoque', recibos: '/recibos'};
const porId = id => document.getElementById(id);
const reais = valor => Number(valor).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
const esc = texto => String(texto).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const camposCliente = ['nome', 'identificacao', 'email'];
const chaveCadastroVip = 'ironstore.cadastroVip';
let cadastroVip = lerCadastroVip();
const camposEndereco = ['cep', 'logradouro', 'numeroEndereco', 'complemento', 'bairro', 'cidade', 'uf'];
const valores = campos => Object.fromEntries(campos.map(id => [id, porId(id).value]));
const formatoValidade = new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Fortaleza'
});

function mensagem(texto, tipo = 'erro', id = 'mensagem') {
    const aviso = porId(id);
    const destino = aviso.closest('dialog')?.open === false ? porId('mensagem') : aviso;
    destino.textContent = texto;
    destino.className = 'aviso ' + tipo;
    destino.hidden = false;
}

function mudarAba(destino, registrar = true) {
    const aba = destino === 'checkout' && !estado?.carrinho.itens.length ? 'carrinho' : destino;
    const caminho = rotas[aba];
    if (location.pathname !== caminho) {
        if (registrar) history.pushState(null, '', caminho);
        else history.replaceState(null, '', caminho);
    }
    if (paginaAtual !== aba) {
        if (paginaAtual === 'checkout') mostrarVipCheckout(null);
        paginaAtual = aba;
        versaoConsultaCartoes++;
        versaoConsultaVip++;
        porId('mensagem').hidden = true;
        if (aba === 'checkout') mostrarEtapa(0, false);
    }
    for (const nome of Object.keys(rotas)) {
        porId('sec-' + nome).classList.toggle('hidden', nome !== aba);
    }
    document.querySelectorAll('.nav-tabs [data-aba]').forEach(link => {
        const atual = link.dataset.aba === aba || (link.dataset.aba === 'carrinho' && aba === 'checkout');
        link.classList.toggle('active', atual);
        if (atual) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
    });
    const titulo = porId('sec-' + aba).querySelector('h1');
    document.title = titulo.textContent + ' | IronStore';
    window.scrollTo(0, 0);
    if (registrar) titulo.focus({preventScroll: true});
}

function abrirRotaAtual() {
    for (const dialogo of document.querySelectorAll('dialog[open]')) {
        if (dialogo.id === 'dialogo-pix') {
            const referencia = dialogo.dataset.referencia;
            const rota = rotaPix(dialogo.dataset.finalidade, 'cancelar');
            fecharPix();
            requisicao(rota, {referencia}).catch(() => {});
        } else {
            dialogo.close();
        }
    }
    const aba = Object.keys(rotas).find(nome => rotas[nome] === location.pathname) ?? 'loja';
    mudarAba(aba, false);
}

function mostrarEtapa(indice, focar = true) {
    etapaAtual = indice;
    const etapas = document.querySelectorAll('#form-compra [data-etapa]');
    etapas.forEach((campo, i) => {
        campo.classList.toggle('hidden', i !== indice);
        campo.disabled = i !== indice;
    });
    porId('etapas-checkout').querySelectorAll('li').forEach((item, i) => {
        item.classList.toggle('etapa-concluida', i < indice);
        if (i === indice) item.setAttribute('aria-current', 'step');
        else item.removeAttribute('aria-current');
    });
    porId('voltar-etapa').textContent = indice === 0 ? 'Voltar ao carrinho' : 'Voltar';
    porId('mensagem-compra').hidden = true;
    consultarVipCheckout();
    atualizarPagamento();
    if (indice === 2) carregarCartoes();
    if (focar) {
        window.scrollTo(0, 0);
        etapas[indice].querySelector('legend').focus({preventScroll: true});
    }
}

async function requisicao(rota, dados) {
    const opcoes = dados ? {method: 'POST', body: new URLSearchParams(dados)} : {};
    const resposta = await fetch(rota, opcoes);
    const corpo = await resposta.json();
    if (!resposta.ok) throw new Error(corpo.erro || 'Operação recusada pelo servidor.');
    return corpo;
}

async function atualizar() {
    estado = await requisicao('/api/estado');
    renderizar();
}

async function operar(rota, dados, sucesso, { receber, atualizarTela = true, aviso = 'mensagem' } = {}) {
    if (ocupado) return;
    ocupado = true;
    bloquearBotoes(true);
    let concluida = false;
    try {
        const resposta = await requisicao(rota, dados);
        concluida = true;
        if (receber) receber(resposta);
        if (atualizarTela) await atualizar();
        if (rota === '/api/venda/finalizar' || rota === '/api/pix/confirmar') {
            mudarAba('recibos');
        }
        if (sucesso) mensagem(sucesso, 'sucesso');
    } catch (erro) {
        mensagem(concluida
            ? 'Operação concluída, mas a tela não atualizou. Recarregue a página antes de continuar.'
            : erro instanceof TypeError ? 'Sem conexão. Confira o terminal do Java e recarregue a página.' : erro.message,
            'erro', aviso);
    } finally {
        ocupado = false;
        bloquearBotoes(false);
    }
}

function bloquearBotoes(bloquear) {
    document.querySelectorAll('button').forEach(botao => {
        botao.disabled = bloquear || botao.dataset.esgotado === 'sim';
    });
    const semItens = !estado?.carrinho.itens.length;
    porId('finalizar').disabled = bloquear || semItens;
    porId('ir-checkout').disabled = bloquear || semItens;
}

function atualizarResumo() {
    if (!estado) return;
    const c = estado.carrinho;
    const desconto = assinaturaCheckout?.ativa ? c.descontoVip : c.descontoPadrao;
    const total = c.subtotal - desconto;
    for (const prefixo of ['', 'checkout-']) {
        porId(prefixo + 'subtotal').textContent = reais(c.subtotal);
        porId(prefixo + 'desconto').textContent = '- ' + reais(desconto);
        porId(prefixo + 'total').textContent = reais(total);
    }
    const selecionada = porId('parcelas').value || '1';
    porId('parcelas').innerHTML = Array.from({length: 12}, (_, i) =>
        `<option value="${i+1}">${i+1}x de aproximadamente ${reais(total/(i+1))}</option>`).join('');
    porId('parcelas').value = selecionada;
}

function mostrarVipCheckout(assinatura) {
    assinaturaCheckout = assinatura;
    let texto = 'Para usar o Clube VIP, informe a identificação e o e-mail do cadastro.';
    if (assinatura) {
        if (assinatura.ativa) texto = 'Clube VIP ativo. Desconto aplicado.';
        else if (assinatura.identificacaoDiferente) texto = 'Este e-mail tem uma assinatura ativa. Use a identificação do cadastro no Clube VIP para aplicar o desconto.';
        else if (assinatura.validaAte) texto = 'Mensalidade vencida. Você pode renovar no Clube VIP.';
        else if (assinatura.cadastrada) texto = 'Mensalidade pendente. Você pode ativar o desconto no Clube VIP.';
        else texto = 'Compra como cliente padrão.';
    }
    porId('status-vip-checkout').textContent = texto;
    const {identificacao, email} = valores(['identificacao', 'email']);
    const mesmosDados = cadastroVip && identificacao.trim() === cadastroVip.identificacao
        && email.trim().toLowerCase() === cadastroVip.email;
    porId('usar-cadastro-vip').classList.toggle('hidden', !cadastroVip || mesmosDados);
    atualizarResumo();
}

async function consultarVipCheckout() {
    if (paginaAtual !== 'checkout') return;
    const consulta = ++versaoConsultaVip;
    const dados = valores(['identificacao', 'email']);
    if (!dados.identificacao.trim() || !dados.email.trim()) {
        mostrarVipCheckout(null);
        return;
    }
    try {
        const assinatura = await requisicao('/api/vip/status', dados);
        if (consulta === versaoConsultaVip) mostrarVipCheckout(assinatura);
    } catch {
        if (consulta === versaoConsultaVip) mostrarVipCheckout(null);
    }
}

function lerCadastroVip() {
    try {
        const dados = JSON.parse(sessionStorage.getItem(chaveCadastroVip));
        if (dados && camposCliente.every(campo => typeof dados[campo] === 'string')) return dados;
    } catch { /* A consulta ao Java continua disponível. */ }
    return null;
}

function preencherCheckoutVip() {
    if (!cadastroVip) return;
    for (const campo of camposCliente) porId(campo).value = cadastroVip[campo];
    mostrarVipCheckout(null);
    consultarVipCheckout();
}

function dadosVip() {
    return Object.fromEntries(camposCliente.map(campo => [campo, porId('vip-' + campo).value]));
}

function mostrarAssinaturaVip(assinatura) {
    const painel = porId('assinatura-vip');
    painel.hidden = !assinatura.cadastrada;
    painel.dataset.ativa = assinatura.ativa;
    const validade = assinatura.validaAte
        ? formatoValidade.format(new Date(assinatura.validaAte)) : '';
    porId('situacao-vip').textContent = assinatura.ativa ? 'VIP ativo'
        : validade ? 'Mensalidade vencida' : 'Cadastro concluído. Mensalidade pendente.';
    porId('validade-vip').textContent = validade
        ? (assinatura.ativa ? 'Válido até ' : 'Venceu em ') + validade : '';
    porId('validade-vip').hidden = !validade;
    porId('gerar-pix-vip').textContent = validade ? 'Renovar mensalidade por Pix' : 'Pagar mensalidade por Pix';
    if (assinatura.ativa) {
        cadastroVip = Object.fromEntries(camposCliente.map(campo => [campo, assinatura[campo]]));
        try {
            sessionStorage.setItem(chaveCadastroVip, JSON.stringify(cadastroVip));
        } catch { /* Os dados continuam disponíveis enquanto a página está aberta. */ }
        preencherCheckoutVip();
    }
}

function atualizarPagamento() {
    const forma = porId('pagamento').value;
    const credito = forma === 'credito';
    const pix = forma === 'pix';
    porId('box-parcelas').classList.toggle('hidden', !credito);
    porId('box-cartao').classList.toggle('hidden', pix);
    porId('cartaoSelecionado').required = !pix;
    porId('cartaoSelecionado').disabled = pix;
    porId('ajuda-pagamento').textContent = pix
        ? 'Gere o QR Code para pagar com Pix.'
        : 'Selecione o cartão que deseja usar no pagamento.';
    const botao = porId('finalizar');
    if (etapaAtual < 2) botao.textContent = 'Continuar';
    else botao.textContent = pix ? 'Gerar QR Code Pix' : 'Finalizar compra';
}

function mostrarCartoes(cartoes, selecionado = porId('cartaoSelecionado').value) {
    const seletor = porId('cartaoSelecionado');
    seletor.replaceChildren(new Option(cartoes.length ? 'Selecione um cartão' : 'Nenhum cartão cadastrado', ''));
    for (const cartao of cartoes) {
        const [ano, mes] = cartao.validade.split('-');
        seletor.add(new Option('•••• ' + cartao.finalNumero + ' · ' + cartao.titular + ' · ' + mes + '/' + ano,
            cartao.id));
    }
    seletor.value = cartoes.some(cartao => cartao.id === selecionado) ? selecionado : cartoes[0]?.id ?? '';
}

async function carregarCartoes() {
    const consulta = ++versaoConsultaCartoes;
    const identificacao = porId('identificacao').value.trim();
    if (paginaAtual !== 'checkout' || etapaAtual !== 2 || !identificacao || porId('pagamento').value === 'pix') return;
    try {
        const { cartoes } = await requisicao('/api/cartoes/listar', { identificacao });
        if (consulta === versaoConsultaCartoes) mostrarCartoes(cartoes);
    } catch {
        if (consulta === versaoConsultaCartoes) {
            mensagem('Não foi possível carregar os cartões cadastrados.', 'erro', 'mensagem-compra');
        }
    }
}

function rotaPix(finalidade, acao) {
    return (finalidade === 'vip' ? '/api/vip/pix/' : '/api/pix/') + acao;
}

function exibirPix(cobranca, finalidade = 'compra') {
    const aba = finalidade === 'vip' ? 'vip' : 'checkout';
    if (paginaAtual !== aba) {
        requisicao(rotaPix(finalidade, 'cancelar'), {referencia: cobranca.referencia}).catch(() => {});
        return;
    }
    const dialogo = porId('dialogo-pix');
    dialogo.dataset.referencia = cobranca.referencia;
    dialogo.dataset.finalidade = finalidade;
    porId('titulo-pix').textContent = finalidade === 'vip' ? 'Mensalidade do Clube VIP' : 'Pagar com Pix';
    porId('mensagem-pix').hidden = true;
    porId('valor-pix').textContent = reais(cobranca.valor);
    porId('qr-pix').src = cobranca.imagem;
    porId('codigo-pix').value = cobranca.codigo;
    dialogo.showModal();
}

function fecharPix() {
    const dialogo = porId('dialogo-pix');
    delete dialogo.dataset.referencia;
    delete dialogo.dataset.finalidade;
    dialogo.close();
}

function cancelarPix() {
    const {referencia, finalidade} = porId('dialogo-pix').dataset;
    operar(rotaPix(finalidade, 'cancelar'), { referencia }, null, {
        receber: () => {
            fecharPix();
            if (finalidade !== 'vip') consultarVipCheckout();
        },
        atualizarTela: false, aviso: 'mensagem-pix'
    });
}

function atualizarCategorias() {
    const campo = porId('filtro-categoria');
    const selecionada = campo.value;
    const categorias = [...new Set(estado.produtos.map(produto => produto.categoria))]
        .sort((a, b) => a.localeCompare(b, 'pt-BR'));
    const opcoes = categorias.map(categoria => new Option(categoria, categoria));
    campo.replaceChildren(new Option('Todas as categorias', ''), ...opcoes);
    campo.value = categorias.includes(selecionada) ? selecionada : '';
}

function normalizarBusca(texto) {
    return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

function renderizarCatalogo() {
    if (!estado) return;
    const busca = normalizarBusca(porId('busca-produto').value);
    const categoria = porId('filtro-categoria').value;
    const produtos = estado.produtos.filter(p => normalizarBusca(p.nome).includes(busca)
        && (!categoria || categoria === p.categoria));
    const quantidade = produtos.length;
    porId('resultado-catalogo').textContent = quantidade === 0 ? 'Nenhum produto encontrado.'
        : quantidade + (quantidade === 1 ? ' produto' : ' produtos');
    porId('limpar-filtros').classList.toggle('hidden', !busca && !categoria);
    porId('grid-produtos').innerHTML = produtos.map(p => {
        const nome = esc(p.nome);
        const titulo = esc(p.nome.replace(/(\d) (g|kg|cápsulas)\b/g, '$1\u00a0$2'));
        const esgotado = p.estoque === 0;
        const imagem = p.imagem
            ? `<img src="${esc(p.imagem)}" width="720" height="720" loading="lazy" decoding="async" alt="Embalagem de ${nome}">`
            : '<span>Imagem indisponível</span>';
        const disponibilidade = p.estoque === 1 ? '1 unidade disponível' : p.estoque + ' unidades disponíveis';
        return `<article class="card produto-card">
            <button type="button" class="produto-media abrir-detalhes" data-acao="detalhes" data-codigo="${esc(p.codigo)}"
                aria-label="Ver detalhes de ${nome}" aria-haspopup="dialog">
                ${imagem}
                <span class="produto-detalhes-rotulo" aria-hidden="true">Ver detalhes</span>
            </button>
            <div class="produto-body">
                <p class="produto-categoria">${esc(p.categoria)}</p>
                <h2 class="produto-nome">${titulo}</h2>
                <p class="produto-estoque${esgotado ? ' produto-estoque-vazio' : ''}">${esgotado ? 'Sem estoque' : disponibilidade}</p>
            </div>
            <div class="produto-footer">
                <strong class="preco">${reais(p.preco)}</strong>
                <button class="btn btn-amber" data-acao="adicionar" data-codigo="${esc(p.codigo)}"
                    aria-label="Adicionar ${nome} ao carrinho" data-esgotado="${esgotado ? 'sim' : 'nao'}" ${esgotado ? 'disabled' : ''}>
                    ${esgotado ? 'Indisponível' : 'Adicionar'}</button>
            </div>
        </article>`;
    }).join('');
    bloquearBotoes(ocupado);
}

function exibirProduto(codigo) {
    const produto = estado.produtos.find(item => item.codigo === codigo);
    if (!produto) return;
    const imagem = porId('detalhe-imagem');
    imagem.classList.toggle('hidden', !produto.imagem);
    if (produto.imagem) {
        imagem.src = produto.imagem;
        imagem.alt = 'Embalagem de ' + produto.nome;
    } else {
        imagem.removeAttribute('src');
    }
    porId('detalhe-sem-imagem').classList.toggle('hidden', Boolean(produto.imagem));
    porId('detalhe-nome').textContent = produto.nome;
    porId('detalhe-categoria').textContent = produto.categoria;
    porId('detalhe-descricao').textContent = produto.descricao;
    porId('detalhe-descricao').hidden = !produto.descricao;
    porId('ficha-embalagem').classList.toggle('hidden', !produto.embalagem);
    porId('detalhe-embalagem').textContent = produto.embalagem;
    porId('ficha-sabores').classList.toggle('hidden', produto.sabores.length === 0);
    porId('detalhe-sabores').textContent = produto.sabores.join(', ');
    porId('detalhe-preco').textContent = reais(produto.preco);
    porId('mensagem-produto').hidden = true;
    const adicionar = porId('adicionar-detalhe');
    adicionar.dataset.codigo = produto.codigo;
    adicionar.dataset.esgotado = produto.estoque === 0 ? 'sim' : 'nao';
    adicionar.textContent = produto.estoque === 0 ? 'Indisponível' : 'Adicionar ao carrinho';
    bloquearBotoes(ocupado);
    porId('dialogo-produto').showModal();
}

function renderizar() {
    porId('mensalidade-vip').textContent = reais(estado.mensalidadeVip);
    atualizarCategorias();
    renderizarCatalogo();
    const itens = estado.carrinho.itens;
    const quantidade = itens.reduce((s, i) => s + i.quantidade, 0);
    porId('badge-carrinho').textContent = quantidade;
    porId('qtd-itens').textContent = quantidade + (quantidade === 1 ? ' item' : ' itens');
    const fotos = new Map(estado.produtos.map(p => [p.codigo, p.imagem]));
    porId('lista-carrinho').innerHTML = itens.length ? itens.map(i => `
        <div class="carrinho-item">
        <div class="carrinho-produto">
            ${fotos.get(i.codigo) ? `<img class="carrinho-foto" src="${esc(fotos.get(i.codigo))}" width="64" height="64" alt="">` : ''}
            <div><strong>${esc(i.nome)}</strong><p class="text-muted">${reais(i.preco)} / un.</p></div>
        </div>
        <div class="qty-controls">
            <button class="qty-btn" data-acao="quantidade" data-codigo="${esc(i.codigo)}" data-quantidade="${i.quantidade-1}" aria-label="Diminuir ${esc(i.nome)}">−</button>
            <span>${i.quantidade}</span>
            <button class="qty-btn" data-acao="quantidade" data-codigo="${esc(i.codigo)}" data-quantidade="${i.quantidade+1}" aria-label="Aumentar ${esc(i.nome)}">+</button>
        </div>
        <strong class="carrinho-preco">${reais(i.subtotal)}</strong>
        <button class="btn btn-danger" data-acao="remover" data-codigo="${esc(i.codigo)}" aria-label="Remover ${esc(i.nome)}">×</button></div>
    `).join('') : '<p class="text-muted">Seu carrinho está vazio. Escolha um suplemento para começar.</p>';
    porId('itens-checkout').innerHTML = itens.map(i => `
        <li><span>${i.quantidade}× ${esc(i.nome)}</span><strong>${reais(i.subtotal)}</strong></li>`).join('');
    porId('tabela-estoque').innerHTML = estado.produtos.map(p => `
        <tr><td>${esc(p.codigo)}</td><td>${esc(p.nome)}</td><td>${reais(p.preco)}</td><td>${p.estoque}</td>
        <td><input class="form-control repor-qtd" id="repor-${esc(p.codigo)}" type="number" min="1" max="1000" step="1" value="5" aria-label="Quantidade a repor de ${esc(p.nome)}">
        <button class="btn btn-sky" data-acao="repor" data-codigo="${esc(p.codigo)}">Repor</button></td></tr>
    `).join('');
    porId('badge-recibos').textContent = estado.recibos.length;
    porId('lista-recibos').replaceChildren();
    if (!estado.recibos.length) porId('lista-recibos').textContent = 'Nenhuma venda concluída ainda.';
    estado.recibos.forEach((recibo, i) => {
        const card = document.createElement('article');
        card.className = 'card';
        const titulo = document.createElement('h2');
        titulo.className = 'section-title text-amber';
        titulo.textContent = i === 0 ? 'Venda mais recente' : 'Venda anterior';
        const texto = document.createElement('pre');
        texto.className = 'recibo-texto';
        texto.textContent = recibo;
        card.append(titulo, texto);
        porId('lista-recibos').append(card);
    });
    atualizarResumo();
    atualizarPagamento();
    bloquearBotoes(ocupado);
}

document.addEventListener('click', evento => {
    const link = evento.target.closest('a[data-aba]');
    if (link && !evento.ctrlKey && !evento.metaKey && !evento.shiftKey && !evento.altKey) {
        evento.preventDefault();
        if (!ocupado) mudarAba(link.dataset.aba);
        return;
    }
    const botao = evento.target.closest('button');
    if (!botao || botao.disabled) return;
    const {acao, codigo, quantidade} = botao.dataset;
    if (acao === 'detalhes') exibirProduto(codigo);
    if (acao === 'adicionar') {
        const dialogo = porId('dialogo-produto');
        const opcoes = dialogo.open
            ? {receber: () => dialogo.close(), aviso: 'mensagem-produto'} : {};
        operar('/api/carrinho/adicionar', {codigo, quantidade: 1}, 'Produto adicionado ao carrinho.', opcoes);
    }
    if (acao === 'quantidade') operar('/api/carrinho/quantidade', {codigo, quantidade}, 'Quantidade atualizada.');
    if (acao === 'remover') operar('/api/carrinho/remover', {codigo}, 'Produto removido.');
    if (acao === 'repor') {
        const campo = porId('repor-' + codigo);
        if (campo.reportValidity()) operar('/api/produtos/repor', {codigo, quantidade: campo.value}, 'Estoque reposto e salvo.');
    }
});
window.addEventListener('popstate', abrirRotaAtual);
porId('busca-produto').addEventListener('input', renderizarCatalogo);
porId('filtro-categoria').addEventListener('change', renderizarCatalogo);
porId('limpar-filtros').addEventListener('click', () => {
    porId('busca-produto').value = '';
    porId('filtro-categoria').value = '';
    renderizarCatalogo();
    porId('busca-produto').focus();
});
porId('fechar-produto').addEventListener('click', () => porId('dialogo-produto').close());
porId('dialogo-produto').addEventListener('cancel', evento => {
    if (ocupado) evento.preventDefault();
});
porId('ir-checkout').addEventListener('click', () => mudarAba('checkout'));
porId('voltar-etapa').addEventListener('click', () => {
    if (etapaAtual === 0) mudarAba('carrinho');
    else mostrarEtapa(etapaAtual - 1);
});
porId('pagamento').addEventListener('change', () => {
    atualizarPagamento();
    carregarCartoes();
});
porId('identificacao').addEventListener('input', () => {
    versaoConsultaCartoes++;
    mostrarCartoes([]);
});
porId('identificacao').addEventListener('change', carregarCartoes);
porId('usar-cadastro-vip').addEventListener('click', preencherCheckoutVip);
for (const id of ['identificacao', 'email']) {
    porId(id).addEventListener('input', () => {
        versaoConsultaVip++;
        mostrarVipCheckout(null);
    });
    porId(id).addEventListener('change', consultarVipCheckout);
}
porId('form-vip').addEventListener('input', () => {
    porId('assinatura-vip').hidden = true;
    porId('mensagem-vip').hidden = true;
});
porId('form-vip').addEventListener('submit', evento => {
    evento.preventDefault();
    porId('mensagem-vip').hidden = true;
    const dados = dadosVip();
    operar('/api/vip/cadastrar', dados, null, {
        receber: assinatura => {
            const atual = dadosVip();
            if (dados.identificacao === atual.identificacao && dados.email === atual.email) {
                mostrarAssinaturaVip(assinatura);
            }
        },
        atualizarTela: false, aviso: 'mensagem-vip'
    });
});
porId('gerar-pix-vip').addEventListener('click', () => {
    const {identificacao, email} = dadosVip();
    operar('/api/vip/pix/gerar', {identificacao, email}, null, {
        receber: cobranca => exibirPix(cobranca, 'vip'),
        atualizarTela: false, aviso: 'mensagem-vip'
    });
});
porId('abrir-cartao').addEventListener('click', () => {
    porId('form-cartao').reset();
    porId('titularCartao').value = porId('nome').value;
    porId('mensagem-cartao').hidden = true;
    porId('dialogo-cartao').showModal();
});
porId('cancelar-cartao').addEventListener('click', () => porId('dialogo-cartao').close());
porId('dialogo-cartao').addEventListener('cancel', evento => {
    if (ocupado) evento.preventDefault();
});
porId('dialogo-cartao').addEventListener('close', () => porId('form-cartao').reset());
porId('form-cartao').addEventListener('submit', evento => {
    evento.preventDefault();
    porId('mensagem-cartao').hidden = true;
    const dados = valores([...camposCliente, 'titularCartao', 'numeroCartao', 'validadeCartao']);
    operar('/api/cartoes/cadastrar', dados, null, {
        receber: ({ cartoes, selecionado }) => {
            versaoConsultaCartoes++;
            mostrarCartoes(cartoes, selecionado);
            porId('dialogo-cartao').close();
            porId('mensagem-compra').hidden = true;
        },
        atualizarTela: false,
        aviso: 'mensagem-cartao'
    });
});
porId('cancelar-pix').addEventListener('click', cancelarPix);
porId('dialogo-pix').addEventListener('cancel', evento => {
    evento.preventDefault();
    cancelarPix();
});
porId('copiar-pix').addEventListener('click', async () => {
    const campo = porId('codigo-pix');
    try {
        await navigator.clipboard.writeText(campo.value);
        mensagem('Código copiado.', 'sucesso', 'mensagem-pix');
    } catch {
        campo.focus();
        campo.select();
        mensagem('Código selecionado. Use Ctrl+C para copiar.', 'sucesso', 'mensagem-pix');
    }
});
porId('confirmar-pix').addEventListener('click', () => {
    const {referencia, finalidade} = porId('dialogo-pix').dataset;
    const mensalidade = finalidade === 'vip';
    const sucesso = mensalidade ? 'Mensalidade confirmada. Benefício VIP ativo.'
        : 'Pagamento confirmado e recibo salvo.';
    operar(rotaPix(finalidade, 'confirmar'), { referencia }, sucesso, {
        receber: assinatura => {
            fecharPix();
            if (mensalidade) mostrarAssinaturaVip(assinatura);
        },
        atualizarTela: !mensalidade, aviso: 'mensagem-pix'
    });
});
porId('form-compra').addEventListener('submit', evento => {
    evento.preventDefault();
    if (paginaAtual !== 'checkout' || !estado?.carrinho.itens.length || ocupado) return;
    porId('mensagem-compra').hidden = true;
    if (etapaAtual < 2) {
        mostrarEtapa(etapaAtual + 1);
        return;
    }
    const dados = valores([...camposCliente, ...camposEndereco, 'pagamento']);
    if (dados.pagamento === 'pix') {
        operar('/api/pix/gerar', dados, null, {
            receber: exibirPix, atualizarTela: false, aviso: 'mensagem-compra'
        });
        return;
    }
    dados.cartao = porId('cartaoSelecionado').value;
    dados.parcelas = dados.pagamento === 'credito' ? porId('parcelas').value : '1';
    operar('/api/venda/finalizar', dados, 'Venda concluída. Estoque atualizado e recibo salvo.', {
        aviso: 'mensagem-compra'
    });
});
preencherCheckoutVip();
atualizar().then(abrirRotaAtual)
    .catch(() => mensagem('Não foi possível carregar a loja. Confira o terminal do Java e recarregue a página.'));
