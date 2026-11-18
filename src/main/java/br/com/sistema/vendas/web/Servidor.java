package br.com.sistema.vendas.web;

import br.com.sistema.vendas.model.*;
import br.com.sistema.vendas.interfaces.MetodoPagamento;
import br.com.sistema.vendas.payment.*;
import br.com.sistema.vendas.exception.*;
import br.com.sistema.vendas.service.VendaService;
import br.com.sistema.vendas.storage.RepositorioBanco;
import com.sun.net.httpserver.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;

public class Servidor {
    private static final BigDecimal LIMITE_SIMULADO = new BigDecimal("1000000");
    private static final BigDecimal MENSALIDADE_VIP = new BigDecimal("19.90");
    private static final Pattern ROTA_IMAGEM = Pattern.compile("/imagens/[a-zA-Z0-9_-]{1,40}\\.jpg");
    private static final FichaProduto FICHA_PADRAO = new FichaProduto("Outros", "", "", List.of());
    private static final Map<String, FichaProduto> FICHAS = Map.of(
            "SUP01", new FichaProduto("Proteínas", "Proteína do soro do leite em pó para preparar shakes.",
                    "900 g", List.of("Chocolate", "Baunilha", "Morango")),
            "SUP02", new FichaProduto("Creatina", "Creatina em pó para misturar à bebida de sua preferência.",
                    "300 g", List.of("Neutro")),
            "SUP03", new FichaProduto("Pré-treino", "Suplemento em pó para preparar uma bebida pré-treino.",
                    "300 g", List.of("Frutas vermelhas", "Limão")),
            "SUP04", new FichaProduto("Aminoácidos", "Suplemento em cápsulas com aminoácidos de cadeia ramificada.",
                    "120 cápsulas", List.of()),
            "SUP05", new FichaProduto("Hipercalóricos", "Mistura em pó para preparar bebidas de maior valor energético.",
                    "3 kg", List.of("Chocolate", "Baunilha")),
            "SUP06", new FichaProduto("Vitaminas", "Suplemento multivitamínico em cápsulas.",
                    "60 cápsulas", List.of()),
            "SUP07", new FichaProduto("Proteínas", "Proteína vegetal em pó à base de ervilha e arroz, para preparar shakes.",
                    "600 g", List.of("Chocolate", "Baunilha")),
            "SUP08", new FichaProduto("Aminoácidos", "Glutamina em pó de sabor neutro para misturar em bebidas.",
                    "300 g", List.of("Neutro")),
            "SUP09", new FichaProduto("Ômegas", "Suplemento de óleo de peixe em cápsulas.",
                    "120 cápsulas", List.of()));

    private final HttpServer http;
    private final List<Produto> produtos = new ArrayList<>();
    private final CarrinhoDeCompras carrinho = new CarrinhoDeCompras();
    private final VendaService vendas = new VendaService();
    private final RepositorioBanco repositorio;
    private final List<String> recibos;
    private final Clock relogio;
    private PixPendente pixPendente;
    private PixMensalidade pixMensalidade;

    private record PixPendente(String referencia, Cliente cliente, EnderecoEntrega endereco) { }
    private record PixMensalidade(String referencia, AssinaturaVip assinatura) { }
    private record FichaProduto(String categoria, String descricao, String embalagem, List<String> sabores) { }

    public Servidor(int porta, Path arquivo) throws IOException {
        this(porta, arquivo, Clock.systemUTC());
    }

    public Servidor(int porta, Path arquivo, Clock relogio) throws IOException {
        this.relogio = Objects.requireNonNull(relogio, "relógio");
        produtos.add(new Produto("SUP01", "Whey Protein 900 g", new BigDecimal("129.90"), 20));
        produtos.add(new Produto("SUP02", "Creatina 300 g", new BigDecimal("89.90"), 35));
        produtos.add(new Produto("SUP03", "Pré-treino 300 g", new BigDecimal("114.90"), 15));
        produtos.add(new Produto("SUP04", "BCAA 120 cápsulas", new BigDecimal("69.90"), 18));
        produtos.add(new Produto("SUP05", "Hipercalórico 3 kg", new BigDecimal("119.90"), 12));
        produtos.add(new Produto("SUP06", "Multivitamínico 60 cápsulas", new BigDecimal("49.90"), 25));
        produtos.add(new Produto("SUP07", "Proteína Vegetal 600 g", new BigDecimal("109.90"), 18));
        produtos.add(new Produto("SUP08", "Glutamina 300 g", new BigDecimal("74.90"), 22));
        produtos.add(new Produto("SUP09", "Ômega 3 120 cápsulas", new BigDecimal("69.90"), 24));
        repositorio = new RepositorioBanco(arquivo);
        recibos = repositorio.carregar(produtos);
        // Somente no próprio computador. Um carrinho compartilhado para a apresentação.
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", porta), 0);
        http.createContext("/", this::atender);
    }

    public void iniciar() { http.start(); }
    public int getPorta() { return http.getAddress().getPort(); }
    public void parar() { http.stop(0); }

    // Mantém as operações da demonstração em sequência, inclusive gravação e estoque.
    private synchronized void atender(HttpExchange troca) throws IOException {
        try {
            String rota = troca.getRequestURI().getPath();
            if (troca.getRequestMethod().equals("GET")) {
                if (rota.equals("/api/estado")) { responder(troca, 200, estado()); return; }
                String recurso = switch (rota) {
                    case "/", "/carrinho", "/checkout", "/clube-vip", "/estoque", "/recibos" -> "index.html";
                    case "/app.js" -> "app.js";
                    case "/styles.css" -> "styles.css";
                    default -> ROTA_IMAGEM.matcher(rota).matches() ? rota.substring(1) : null;
                };
                if (recurso != null) { servir(troca, recurso); return; }
            } else if (troca.getRequestMethod().equals("POST")) {
                // Bloqueia formulários de outras páginas contra o servidor local.
                String origem = troca.getRequestHeaders().getFirst("Origin");
                String host = troca.getRequestHeaders().getFirst("Host");
                if (origem != null && !origem.equals("http://" + host)) {
                    responder(troca, 403, Map.of("erro", "Origem não permitida.")); return;
                }
                Map<String, String> dados = formulario(troca);
                switch (rota) {
                    case "/api/carrinho/adicionar" -> {
                        Produto p = produto(dados);
                        carrinho.adicionarItem(p, inteiro(dados, "quantidade", 1, 1000));
                    }
                    case "/api/carrinho/quantidade" ->
                        carrinho.atualizarQuantidade(produto(dados), inteiro(dados, "quantidade", 0, 1000));
                    case "/api/carrinho/remover" -> carrinho.removerItem(produto(dados));
                    case "/api/cartoes/listar" -> {
                        responder(troca, 200, Map.of("cartoes", cartoesJson(texto(dados, "identificacao"))));
                        return;
                    }
                    case "/api/cartoes/cadastrar" -> {
                        Cliente cliente = cliente(dados);
                        CartaoCadastrado cartao = CartaoCadastrado.cadastrar(texto(dados, "titularCartao"),
                                texto(dados, "numeroCartao"), texto(dados, "validadeCartao"));
                        String id = repositorio.salvarCartao(cliente, cartao);
                        responder(troca, 200, Map.of("selecionado", id,
                                "cartoes", cartoesJson(cliente.getIdentificacao())));
                        return;
                    }
                    case "/api/vip/cadastrar" -> {
                        Cliente candidato = new ClientePadrao(texto(dados, "nome"), texto(dados, "identificacao"),
                                texto(dados, "email"));
                        responder(troca, 200, statusVip(repositorio.cadastrarVip(candidato)));
                        return;
                    }
                    case "/api/vip/status" -> {
                        AssinaturaVip assinatura = buscarVip(dados).orElse(null);
                        Map<String, Object> status = new HashMap<>(statusVip(assinatura));
                        if (assinatura == null && repositorio.existeVipAtivoComEmail(texto(dados, "email"), relogio.instant())) {
                            status.put("identificacaoDiferente", true);
                        }
                        responder(troca, 200, status);
                        return;
                    }
                    case "/api/vip/pix/gerar" -> {
                        AssinaturaVip assinatura = buscarVip(dados).orElseThrow(() ->
                                new IllegalArgumentException("Cadastre-se no Clube VIP antes de pagar a mensalidade."));
                        CobrancaPix cobranca = CobrancaPix.gerar(MENSALIDADE_VIP);
                        pixMensalidade = new PixMensalidade(cobranca.referencia(), assinatura);
                        responder(troca, 200, cobrancaJson(cobranca));
                        return;
                    }
                    case "/api/vip/pix/confirmar" -> {
                        PixMensalidade pix = pixMensalidade;
                        if (pix == null || !pix.referencia().equals(dados.get("referencia"))) {
                            throw new IllegalStateException("Este Pix de mensalidade já foi encerrado. Gere um novo QR Code.");
                        }
                        MetodoPagamento meio = new PagamentoPix(CobrancaPix.CHAVE_DEMONSTRACAO, pix.referencia());
                        ComprovantePagamento pagamento = meio.processarPagamento(MENSALIDADE_VIP);
                        AssinaturaVip assinatura = repositorio.salvarMensalidade(pix.assinatura(), pagamento, relogio.instant());
                        pixMensalidade = null;
                        responder(troca, 200, statusVip(assinatura));
                        return;
                    }
                    case "/api/vip/pix/cancelar" -> {
                        if (pixMensalidade != null && pixMensalidade.referencia().equals(dados.get("referencia"))) {
                            pixMensalidade = null;
                        }
                    }
                    case "/api/pix/gerar" -> {
                        Cliente cliente = cliente(dados);
                        EnderecoEntrega endereco = endereco(dados);
                        Pedido pedido = new Pedido(cliente, carrinho.getItens(), endereco);
                        CobrancaPix cobranca = CobrancaPix.gerar(pedido);
                        pixPendente = new PixPendente(cobranca.referencia(), cliente, endereco);
                        responder(troca, 200, cobrancaJson(cobranca));
                        return;
                    }
                    case "/api/pix/confirmar" -> {
                        PixPendente pix = pixPendente;
                        if (pix == null || !pix.referencia().equals(dados.get("referencia"))) {
                            throw new IllegalStateException("Este Pix já foi encerrado. Gere um novo QR Code para comprar.");
                        }
                        Cliente cliente = pix.cliente();
                        boolean ativo = vipAtivo(cliente.getIdentificacao(), cliente.getEmail());
                        if (ativo != (cliente instanceof ClienteVip)) {
                            pixPendente = null;
                            throw new IllegalStateException("Seu status no Clube VIP mudou. Gere um novo QR Code para atualizar o valor.");
                        }
                        MetodoPagamento pagamento = new PagamentoPix(CobrancaPix.CHAVE_DEMONSTRACAO, pix.referencia());
                        responder(troca, 200, finalizarVenda(cliente, pix.endereco(), pagamento, "pix", 1));
                        return;
                    }
                    case "/api/pix/cancelar" -> {
                        if (pixPendente != null && pixPendente.referencia().equals(dados.get("referencia"))) {
                            pixPendente = null;
                        }
                    }
                    case "/api/venda/finalizar" -> {
                        Cliente cliente = cliente(dados);
                        EnderecoEntrega endereco = endereco(dados);
                        String tipo = texto(dados, "pagamento");
                        int parcelas = inteiro(dados, "parcelas", 1, 12);
                        MetodoPagamento pagamento = pagamentoCartao(dados, cliente, tipo, parcelas);
                        responder(troca, 200, finalizarVenda(cliente, endereco, pagamento, tipo, parcelas));
                        return;
                    }
                    case "/api/produtos/repor" -> {
                        Produto p = produto(dados);
                        int quantidade = inteiro(dados, "quantidade", 1, 1000);
                        p.reporEstoque(quantidade);
                        try { repositorio.reporEstoque(p.getCodigo(), quantidade); }
                        catch (RuntimeException e) { p.debitarEstoque(quantidade); throw e; }
                    }
                    default -> { responder(troca, 404, Map.of("erro", "Operação não encontrada.")); return; }
                }
                if (rota.startsWith("/api/carrinho/")) pixPendente = null;
                responder(troca, 200, Map.of("ok", true)); return;
            }
            responder(troca, 404, Map.of("erro", "Página não encontrada."));
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException
                 | EstoqueInsuficienteException | PagamentoRecusadoException e) {
            // Erro de gravação é interno. Os demais avisos podem ser corrigidos na tela.
            int status = (e.getCause() instanceof IOException || e.getCause() instanceof java.sql.SQLException) ? 500 : 400;
            responder(troca, status, Map.of("erro", e.getMessage() == null ? "Dados inválidos." : e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            responder(troca, 500, Map.of("erro", "Erro interno. Confira o terminal do Java."));
        } finally { troca.close(); }
    }

    private Produto produto(Map<String, String> dados) {
        String codigo = texto(dados, "codigo");
        return produtos.stream().filter(p -> p.getCodigo().equals(codigo)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
    }

    private Cliente cliente(Map<String, String> dados) {
        String nome = texto(dados, "nome");
        String identificacao = texto(dados, "identificacao");
        String email = texto(dados, "email");
        if (vipAtivo(identificacao, email)) return new ClienteVip(nome, identificacao, email);
        return new ClientePadrao(nome, identificacao, email);
    }

    private boolean vipAtivo(String identificacao, String email) {
        return repositorio.buscarVip(identificacao, email)
                .filter(assinatura -> assinatura.ativaEm(relogio.instant()))
                .isPresent();
    }

    private Optional<AssinaturaVip> buscarVip(Map<String, String> dados) {
        return repositorio.buscarVip(texto(dados, "identificacao"), texto(dados, "email"));
    }

    private Map<String, Object> statusVip(AssinaturaVip assinatura) {
        if (assinatura == null) {
            return Map.of("cadastrada", false, "ativa", false, "validaAte", "", "nome", "");
        }
        String validade = assinatura.validaAte() == null ? "" : assinatura.validaAte().toString();
        return Map.of("cadastrada", true, "ativa", assinatura.ativaEm(relogio.instant()),
                "validaAte", validade, "nome", assinatura.nome(),
                "identificacao", assinatura.identificacao(), "email", assinatura.email());
    }

    private Map<String, Object> cobrancaJson(CobrancaPix cobranca) {
        return Map.of("referencia", cobranca.referencia(), "valor", cobranca.valor(),
                "codigo", cobranca.codigo(), "imagem", cobranca.imagem());
    }

    private EnderecoEntrega endereco(Map<String, String> dados) {
        return new EnderecoEntrega(dados.get("cep"), dados.get("logradouro"), dados.get("numeroEndereco"),
                dados.get("complemento"), dados.get("bairro"), dados.get("cidade"), dados.get("uf"));
    }

    private List<Map<String, String>> cartoesJson(String identificacao) {
        return repositorio.listarCartoes(identificacao).stream()
                .map(cartao -> Map.of("id", cartao.id(), "titular", cartao.titular(),
                        "finalNumero", cartao.finalNumero(), "validade", cartao.validade().toString()))
                .toList();
    }

    private MetodoPagamento pagamentoCartao(Map<String, String> dados, Cliente cliente, String tipo, int parcelas) {
        if (!tipo.equals("credito") && !tipo.equals("debito")) {
            throw new IllegalArgumentException("Selecione um cartão ou gere o QR Code para pagar com Pix.");
        }
        if (tipo.equals("debito") && parcelas != 1) {
            throw new IllegalArgumentException("Débito é à vista.");
        }
        CartaoCadastrado cartao = repositorio.listarCartoes(cliente.getIdentificacao()).stream()
                .filter(item -> item.id().equals(dados.get("cartao")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cadastre e selecione um cartão deste cliente."));
        cartao.validarValidade();
        BigDecimal disponivel = dados.containsKey("disponivel") ? decimal(dados, "disponivel") : LIMITE_SIMULADO;
        return tipo.equals("credito") ? new PagamentoCartao(cartao, parcelas, disponivel)
                : new PagamentoDebito(cartao, disponivel);
    }

    private Map<String, String> finalizarVenda(Cliente cliente, EnderecoEntrega endereco,
            MetodoPagamento pagamento, String tipo, int parcelas) {
        Recibo recibo = vendas.finalizarCompra(carrinho, cliente, endereco, pagamento,
                novo -> repositorio.salvarVenda(novo, tipo, parcelas));
        pixPendente = null;
        String texto = recibo.formatar();
        recibos.add(0, texto);
        return Map.of("recibo", texto);
    }

    private Map<String, Object> produtoJson(Produto p) {
        FichaProduto ficha = FICHAS.getOrDefault(p.getCodigo(), FICHA_PADRAO);
        String imagem = "/imagens/" + p.getCodigo() + ".jpg";
        if (!ROTA_IMAGEM.matcher(imagem).matches() || Servidor.class.getResource("/public" + imagem) == null) {
            imagem = "";
        }
        return Map.of("codigo", p.getCodigo(), "nome", p.getNome(), "preco", p.getPreco(),
                "estoque", p.getQuantidadeEstoque(), "imagem", imagem, "categoria", ficha.categoria(),
                "descricao", ficha.descricao(), "embalagem", ficha.embalagem(), "sabores", ficha.sabores());
    }

    private Map<String, Object> estado() {
        List<Map<String, Object>> itens = new ArrayList<>();
        for (ItemPedido i : carrinho.getItens()) {
            itens.add(Map.of("codigo", i.getProduto().getCodigo(), "nome", i.getProduto().getNome(),
                "quantidade", i.getQuantidadeAdquirida(), "preco", i.getPrecoUnitario(),
                "subtotal", i.calcularSubtotal()));
        }
        BigDecimal subtotal = carrinho.calcularTotalParcial();
        Cliente padrao = new ClientePadrao("Exemplo", "DEMO", "demo@exemplo.com");
        Cliente vip = new ClienteVip("Exemplo", "DEMO", "demo@exemplo.com");
        return Map.of("produtos", produtos.stream().map(this::produtoJson).toList(),
            "carrinho", Map.of("itens", itens, "subtotal", subtotal,
                "descontoPadrao", padrao.calcularDesconto(subtotal), "descontoVip", vip.calcularDesconto(subtotal)),
            "recibos", recibos, "mensalidadeVip", MENSALIDADE_VIP);
    }

    private Map<String, String> formulario(HttpExchange troca) throws IOException {
        String tipo = troca.getRequestHeaders().getFirst("Content-Type");
        if (tipo == null || !tipo.startsWith("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Envie os campos do formulário.");
        }
        byte[] bytes = troca.getRequestBody().readNBytes(16385);
        if (bytes.length > 16384) throw new IllegalArgumentException("Formulário muito grande.");
        Map<String, String> dados = new HashMap<>();
        for (String campo : new String(bytes, StandardCharsets.UTF_8).split("&")) {
            String[] partes = campo.split("=", 2);
            if (partes.length == 2) {
                String chave = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
                if (dados.containsKey(chave)) throw new IllegalArgumentException("Campo repetido.");
                dados.put(chave, URLDecoder.decode(partes[1], StandardCharsets.UTF_8));
            }
        }
        return dados;
    }

    private String texto(Map<String, String> dados, String campo) {
        String valor = dados.getOrDefault(campo, "").strip();
        if (valor.isEmpty() || valor.length() > 120) throw new IllegalArgumentException("Preencha corretamente: " + campo);
        return valor;
    }

    private int inteiro(Map<String, String> dados, String campo, int minimo, int maximo) {
        try {
            int valor = Integer.parseInt(texto(dados, campo));
            if (valor >= minimo && valor <= maximo) return valor;
        } catch (NumberFormatException ignorado) { }
        throw new IllegalArgumentException(campo + ": use um inteiro entre " + minimo + " e " + maximo + ".");
    }

    private BigDecimal decimal(Map<String, String> dados, String campo) {
        try {
            BigDecimal valor = new BigDecimal(texto(dados, campo).replace(',', '.'));
            if (valor.signum() >= 0 && valor.compareTo(LIMITE_SIMULADO) <= 0 && valor.scale() <= 2)
                return valor;
        } catch (NumberFormatException ignorado) { }
        throw new IllegalArgumentException("Informe saldo/limite de 0 até 1.000.000, com até dois decimais.");
    }

    private void responder(HttpExchange troca, int status, Object dados) throws IOException {
        byte[] bytes = Json.escrever(dados).getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        troca.getResponseHeaders().set("Cache-Control", "no-store");
        troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
    }

    private void servir(HttpExchange troca, String nome) throws IOException {
        try (InputStream recurso = Servidor.class.getResourceAsStream("/public/" + nome)) {
            if (recurso == null) { responder(troca, 404, Map.of("erro", "Tela não encontrada.")); return; }
            byte[] bytes = recurso.readAllBytes();
            String tipo = switch (nome.substring(nome.lastIndexOf('.') + 1)) {
                case "jpg" -> "image/jpeg";
                case "css" -> "text/css; charset=utf-8";
                case "js" -> "text/javascript; charset=utf-8";
                default -> "text/html; charset=utf-8";
            };
            troca.getResponseHeaders().set("Content-Type", tipo);
            troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            troca.sendResponseHeaders(200, bytes.length);
            troca.getResponseBody().write(bytes);
        }
    }
}
