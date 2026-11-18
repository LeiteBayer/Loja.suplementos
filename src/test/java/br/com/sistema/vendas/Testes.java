package br.com.sistema.vendas;

import br.com.sistema.vendas.model.*;
import br.com.sistema.vendas.payment.*;
import br.com.sistema.vendas.service.VendaService;
import br.com.sistema.vendas.enums.EstadoPedido;
import br.com.sistema.vendas.exception.*;
import br.com.sistema.vendas.web.Servidor;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

// Testes executáveis sem bibliotecas adicionais. Não dependem de assertions habilitadas.
public class Testes {
    private static int verificacoes;
    private static final Cliente CLIENTE = new ClientePadrao("Cliente teste", "DEMO", "teste@exemplo.com");
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) throw new AssertionError(descricao);
        verificacoes++;
    }
    private static void falha(Class<? extends RuntimeException> tipo, Runnable acao) {
        try { acao.run(); } catch (RuntimeException e) {
            conferir(tipo.isInstance(e), "Exceção esperada: " + tipo.getSimpleName()); return;
        }
        throw new AssertionError("Esperava " + tipo.getSimpleName());
    }
    private static BigDecimal valor(String v) { return new BigDecimal(v); }
    private static CarrinhoDeCompras carrinho(Produto produto) {
        CarrinhoDeCompras c = new CarrinhoDeCompras(); c.adicionarItem(produto, 1); return c;
    }

    public static void main(String[] args) throws Exception {
        VendaService vendas = new VendaService();
        Produto produto = new Produto("T", "Creatina", valor("89.90"), 10);
        CarrinhoDeCompras c = carrinho(produto);
        Recibo pix = vendas.finalizarCompra(c, CLIENTE, new PagamentoPix("teste@exemplo.com"));
        conferir(pix.getValorPago().compareTo(valor("89.90")) == 0, "Total Pix");
        conferir(produto.getQuantidadeEstoque() == 9 && c.estaVazio(), "Baixa de estoque e carrinho limpo");
        c = carrinho(produto);
        PagamentoCartao credito = new PagamentoCartao("Teste", "4111111111111111", 3, valor("100.00"));
        vendas.finalizarCompra(c, CLIENTE, credito);
        conferir(credito.getLimiteDisponivel().compareTo(valor("10.10")) == 0, "Crédito reduz limite");
        conferir(vendas.finalizarCompra(carrinho(produto), CLIENTE, new PagamentoDebito(valor("89.90")))
            .getMetodoPagamento().contains("débito"), "Débito aprovado com saldo exato");
        int estoque = produto.getQuantidadeEstoque();
        CarrinhoDeCompras recusado = carrinho(produto);
        falha(PagamentoRecusadoException.class, () -> vendas.finalizarCompra(recusado, CLIENTE, new PagamentoDebito(valor("1"))));
        conferir(produto.getQuantidadeEstoque() == estoque && !recusado.estaVazio(), "Débito recusado preserva estoque e carrinho");
        falha(PagamentoRecusadoException.class, () -> vendas.finalizarCompra(recusado, CLIENTE,
            new PagamentoCartao("Teste", "4111111111111111", 1, valor("1"))));
        conferir(produto.getQuantidadeEstoque() == estoque, "Crédito recusado devolve estoque");
        falha(EstoqueInsuficienteException.class, () -> recusado.adicionarItem(produto, 100));
        falha(IllegalArgumentException.class, () -> recusado.adicionarItem(produto, -1));
        falha(IllegalStateException.class, () -> vendas.finalizarCompra(new CarrinhoDeCompras(), CLIENTE, new PagamentoPix("x")));
        falha(IllegalArgumentException.class, () -> new Produto("X", "Inválido", valor("0"), 1));
        falha(IllegalArgumentException.class, () -> new ClientePadrao("Teste", "T", "email-invalido"));
        falha(IllegalArgumentException.class, () -> new PagamentoCartao("Teste", "4111111111111111", 13, valor("1000")));
        conferir(CLIENTE.calcularDesconto(valor("500")).compareTo(valor("25")) == 0, "Padrão: 5% em 500");
        Cliente vip = new ClienteVip("Teste", "T", "teste@exemplo.com");
        conferir(vip.calcularDesconto(valor("700.00")).compareTo(valor("70.00")) == 0,
                "VIP aplica 10% no subtotal de R$ 700,00");
        conferir(vip.calcularDesconto(valor("700.01")).compareTo(valor("105.00")) == 0,
                "VIP aplica 15% no subtotal de R$ 700,01");
        conferir(!EstadoPedido.ABERTO.podeTransicionarPara(EstadoPedido.CONCLUIDO), "Estado não pula pagamento");
        Pedido pedido = new Pedido(CLIENTE, recusado.getItens());
        falha(TransicaoEstadoInvalidaException.class, pedido::concluir);
        Produto preco = new Produto("P", "Whey", valor("100"), 2);
        CarrinhoDeCompras congelado = carrinho(preco); preco.alterarPreco(valor("200"));
        conferir(congelado.calcularTotalParcial().compareTo(valor("100")) == 0, "Preço do item preservado");
        PagamentoDebito debito = new PagamentoDebito(valor("100"));
        falha(IllegalStateException.class, () -> vendas.finalizarCompra(recusado, CLIENTE, debito,
            recibo -> { throw new IllegalStateException("Falha de gravação simulada"); }));
        conferir(produto.getQuantidadeEstoque() == estoque && !recusado.estaVazio(), "Gravação falha preserva estoque e carrinho");
        conferir(debito.getSaldoDisponivel().compareTo(valor("100")) == 0, "Falha de gravação estorna saldo simulado");
        testarHttp();
        testarVip();
        System.out.println("OK: " + verificacoes + " verificações, incluindo API, três pagamentos, Clube VIP e persistência.");
    }

    private static String base;
    private static HttpResponse<String> get() throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(base + "/api/estado")).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(String rota, Map<String, String> dados) throws Exception {
        StringJoiner form = new StringJoiner("&");
        dados.forEach((k, v) -> form.add(URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8)));
        return HTTP.send(HttpRequest.newBuilder(URI.create(base + rota))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static Map<String, String> compra(String pagamento, String saldo) {
        return new HashMap<>(Map.ofEntries(Map.entry("nome", "Cliente Teste"), Map.entry("identificacao", "DEMO-01"),
                Map.entry("email", "teste@exemplo.com"), Map.entry("tipoCliente", "padrao"),
                Map.entry("pagamento", pagamento), Map.entry("parcelas", "1"), Map.entry("disponivel", saldo),
                Map.entry("cep", "60000000"), Map.entry("logradouro", "Rua das Palmeiras"),
                Map.entry("numeroEndereco", "120"), Map.entry("bairro", "Centro"),
                Map.entry("cidade", "Fortaleza"), Map.entry("uf", "CE")));
    }

    private static String campoJson(String resposta, String campo) {
        var resultado = Pattern.compile("\"" + campo + "\":\"([^\"]+)\"").matcher(resposta);
        if (!resultado.find()) throw new AssertionError("Campo ausente na resposta: " + campo);
        return resultado.group(1);
    }

    private static void conferirQrCode(String resposta, String valor) throws Exception {
        String codigo = campoJson(resposta, "codigo");
        String imagem = campoJson(resposta, "imagem");
        String prefixo = "data:image/png;base64,";
        conferir(imagem.startsWith(prefixo), "Pix fornece PNG para o navegador");
        byte[] png = Base64.getDecoder().decode(imagem.substring(prefixo.length()));
        BufferedImage qr = ImageIO.read(new ByteArrayInputStream(png));
        int largura = qr.getWidth();
        int altura = qr.getHeight();
        int[] pixels = qr.getRGB(0, 0, largura, altura, null, 0, largura);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(largura, altura, pixels)));
        String lido = new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.PURE_BARCODE, true)).getText();
        conferir(lido.equals(codigo), "QR Code contém o mesmo Pix Copia e Cola");
        String campoValor = String.format(Locale.ROOT, "54%02d%s", valor.length(), valor);
        conferir(codigo.startsWith("000201") && codigo.contains(campoValor)
                && codigo.contains(campoJson(resposta, "referencia")), "Código Pix inclui total do Java e referência");
    }
    private static void testarHttp() throws Exception {
        Path pasta = Files.createTempDirectory("loja-teste-");
        Path dados = pasta.resolve("loja.db");
        Servidor servidor = new Servidor(0, dados);
        servidor.iniciar();
        try {
            base = "http://127.0.0.1:" + servidor.getPorta();
            conferir(get().statusCode() == 200, "Estado HTTP");
            conferir(post("/api/carrinho/adicionar", Map.of("codigo", "SUP01", "quantidade", "1.5")).statusCode() == 400, "Quantidade fracionada recusada na API");
            conferir(post("/api/carrinho/adicionar", Map.of("codigo", "SUP01", "quantidade", "-1")).statusCode() == 400, "Quantidade negativa recusada na API");
            conferir(post("/api/pix/gerar", compra("pix", "0")).statusCode() == 400, "Carrinho vazio recusado na API");
            conferir(post("/api/carrinho/adicionar", Map.of("codigo", "SUP01", "quantidade", "1")).statusCode() == 200,
                    "Adicionar para Pix");
            Map<String, String> semEndereco = compra("pix", "0");
            semEndereco.remove("logradouro");
            conferir(post("/api/pix/gerar", semEndereco).statusCode() == 400, "Endereço obrigatório para Pix");
            HttpResponse<String> pix = post("/api/pix/gerar", compra("pix", "0"));
            conferir(pix.statusCode() == 200, "Pix gerado na API");
            conferirQrCode(pix.body(), "129.90");
            conferir(get().body().contains("\"recibos\":[]") && get().body().contains("\"estoque\":20"),
                    "Gerar Pix preserva estoque e ainda não emite recibo");
            Map<String, String> confirmacao = Map.of("referencia", campoJson(pix.body(), "referencia"));
            conferir(post("/api/pix/cancelar", confirmacao).statusCode() == 200, "Pix pode ser cancelado");
            conferir(post("/api/pix/confirmar", confirmacao).statusCode() == 400, "Pix cancelado não pode ser confirmado");

            pix = post("/api/pix/gerar", compra("pix", "0"));
            confirmacao = Map.of("referencia", campoJson(pix.body(), "referencia"));
            post("/api/carrinho/quantidade", Map.of("codigo", "SUP01", "quantidade", "2"));
            conferir(post("/api/pix/confirmar", confirmacao).statusCode() == 400, "Alteração do carrinho invalida o Pix anterior");
            post("/api/carrinho/quantidade", Map.of("codigo", "SUP01", "quantidade", "1"));
            pix = post("/api/pix/gerar", compra("pix", "0"));
            confirmacao = Map.of("referencia", campoJson(pix.body(), "referencia"));
            HttpResponse<String> confirmado = post("/api/pix/confirmar", confirmacao);
            conferir(confirmado.statusCode() == 200 && confirmado.body().contains(confirmacao.get("referencia")),
                    "Confirmação do Pix emite recibo com a referência");
            conferir(post("/api/pix/confirmar", confirmacao).statusCode() == 400, "Pix concluído não pode ser pago duas vezes");

            Map<String, String> cadastro = compra("credito", "1000");
            cadastro.put("titularCartao", "Cliente Teste");
            cadastro.put("validadeCartao", YearMonth.now().plusYears(2).toString());
            cadastro.put("numeroCartao", "1234");
            conferir(post("/api/cartoes/cadastrar", cadastro).statusCode() == 400, "Cadastro recusa número incompleto");
            cadastro.put("numeroCartao", "4111 1111 1111 1111");
            HttpResponse<String> cadastrado = post("/api/cartoes/cadastrar", cadastro);
            conferir(cadastrado.statusCode() == 200 && cadastrado.body().contains("\"finalNumero\":\"1111\"")
                    && !cadastrado.body().contains("4111 1111 1111 1111")
                    && !cadastrado.body().contains("4111111111111111"), "Cadastro devolve apenas os últimos dígitos");
            String cartao = campoJson(cadastrado.body(), "selecionado");
            conferir(cartao.equals(campoJson(post("/api/cartoes/cadastrar", cadastro).body(), "selecionado")),
                    "Repetir cadastro reutiliza a referência do cartão");

            for (String tipo : List.of("credito", "debito")) {
                conferir(post("/api/carrinho/adicionar", Map.of("codigo", "SUP01", "quantidade", "1")).statusCode() == 200, "Adicionar via API");
                Map<String, String> dadosVenda = compra(tipo, "1");
                conferir(post("/api/venda/finalizar", dadosVenda).statusCode() == 400, "Cartão obrigatório para " + tipo);
                dadosVenda.put("cartao", cartao);
                conferir(post("/api/venda/finalizar", dadosVenda).statusCode() == 400, "Recusa HTTP de " + tipo);
                dadosVenda.remove("disponivel");
                HttpResponse<String> venda = post("/api/venda/finalizar", dadosVenda);
                conferir(venda.statusCode() == 200 && venda.body().contains("final 1111"), "Venda HTTP com cartão salvo: " + tipo);
            }
            conferir(Files.exists(dados), "Arquivo de persistência criado");
            String antes = get().body();
            conferir(antes.contains("Cartão de débito") && antes.contains("Cartão de crédito") && antes.contains("Pix"), "Três recibos no histórico");
            conferir(antes.contains("\"estoque\":17"), "Estoque reduziu de 20 para 17");
            conferir(post("/api/produtos/repor", Map.of("codigo", "SUP01", "quantidade", "3")).statusCode() == 200, "Reposição HTTP");
            Map<String,String> invalida = compra("debito", "1000"); invalida.put("parcelas", "2");
            invalida.put("cartao", cartao);
            post("/api/carrinho/adicionar", Map.of("codigo", "SUP01", "quantidade", "1"));
            conferir(post("/api/venda/finalizar", invalida).statusCode() == 400, "Débito parcelado recusado");
            Map<String, String> outroCliente = compra("credito", "1000");
            outroCliente.put("identificacao", "OUTRO");
            outroCliente.put("cartao", cartao);
            conferir(post("/api/venda/finalizar", outroCliente).statusCode() == 400, "Cartão de outro cliente recusado");
            Map<String,String> email = compra("pix", "1000"); email.put("email", "incorreto");
            conferir(post("/api/pix/gerar", email).statusCode() == 400, "E-mail inválido recusado pelo Java");
        } finally { servidor.parar(); }
        Servidor reaberto = new Servidor(0, dados);
        reaberto.iniciar();
        try {
            base = "http://127.0.0.1:" + reaberto.getPorta();
            String estado = get().body();
            conferir(estado.contains("Cartão de débito"), "Histórico recuperado ao reiniciar");
            conferir(estado.contains("Rua das Palmeiras") && estado.contains("60000-000"), "Endereço no recibo ao reiniciar");
            conferir(estado.contains("\"estoque\":20"), "Reposição persistiu");
            conferir(estado.contains("\"itens\":[]"), "Carrinho temporário não persiste");
            String cartoes = post("/api/cartoes/listar", Map.of("identificacao", "DEMO-01")).body();
            conferir(cartoes.contains("\"finalNumero\":\"1111\""), "Cartão mascarado pode ser selecionado ao reiniciar");
        } finally {
            reaberto.parar();
            Files.deleteIfExists(dados); Files.deleteIfExists(pasta);
        }
    }

    private static void testarVip() throws Exception {
        Path pasta = Files.createTempDirectory("loja-vip-teste-");
        Path dados = pasta.resolve("loja.db");
        Instant vencimento = Instant.parse("2026-02-28T15:00:00Z");
        AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-01-31T15:00:00Z"));
        Clock relogio = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zona) { return Clock.fixed(instant(), zona); }
            @Override public Instant instant() { return agora.get(); }
        };
        Map<String, String> cliente = compra("pix", "0");
        cliente.put("tipoCliente", "vip");
        Servidor servidor = new Servidor(0, dados, relogio);
        servidor.iniciar();
        try {
            base = "http://127.0.0.1:" + servidor.getPorta();
            conferir(post("/api/vip/pix/gerar", cliente).statusCode() == 400, "Mensalidade exige cadastro");
            post("/api/carrinho/adicionar", Map.of("codigo", "SUP01", "quantidade", "1"));
            conferir(post("/api/pix/gerar", cliente).body().contains("\"valor\":129.90"),
                    "O tipo recebido do checkout não concede desconto VIP");

            HttpResponse<String> inscricao = post("/api/vip/cadastrar", cliente);
            conferir(inscricao.statusCode() == 200 && inscricao.body().contains("\"cadastrada\":true")
                    && inscricao.body().contains("\"ativa\":false"), "Cadastro sozinho não ativa o plano");
            Map<String, String> outroEmail = new HashMap<>(cliente);
            outroEmail.put("email", "outro@exemplo.com");
            conferir(post("/api/vip/cadastrar", outroEmail).statusCode() == 400,
                    "Cadastro existente mantém o e-mail original");
            HttpResponse<String> mensalidade = post("/api/vip/pix/gerar", cliente);
            conferir(mensalidade.statusCode() == 200 && mensalidade.body().contains("\"valor\":19.90"),
                    "Pix da mensalidade tem o valor do plano");
            conferirQrCode(mensalidade.body(), "19.90");
            Map<String, String> confirmar = Map.of("referencia", campoJson(mensalidade.body(), "referencia"));
            conferir(post("/api/vip/status", cliente).body().contains("\"ativa\":false"),
                    "Gerar QR Code ainda não libera o benefício");
            conferir(post("/api/vip/pix/cancelar", confirmar).statusCode() == 200
                    && post("/api/vip/pix/confirmar", confirmar).statusCode() == 400,
                    "Mensalidade cancelada não pode ser confirmada");

            mensalidade = post("/api/vip/pix/gerar", cliente);
            confirmar = Map.of("referencia", campoJson(mensalidade.body(), "referencia"));
            HttpResponse<String> pagamento = post("/api/vip/pix/confirmar", confirmar);
            conferir(pagamento.statusCode() == 200 && pagamento.body().contains("\"ativa\":true")
                    && campoJson(pagamento.body(), "validaAte").equals(vencimento.toString())
                    && campoJson(pagamento.body(), "identificacao").equals(cliente.get("identificacao"))
                    && campoJson(pagamento.body(), "email").equals(cliente.get("email")),
                    "Confirmação ativa o plano por um mês de calendário");
            conferir(post("/api/vip/pix/confirmar", confirmar).statusCode() == 400,
                    "A mesma mensalidade não pode ser confirmada duas vezes");
            String estado = get().body();
            conferir(estado.contains("\"recibos\":[]") && estado.contains("\"estoque\":20"),
                    "A mensalidade não grava venda nem altera estoque");

            conferir(post("/api/pix/gerar", outroEmail).body().contains("\"valor\":129.90"),
                    "Outro e-mail com a mesma identificação recebe o preço padrão");
            Map<String, String> outroId = new HashMap<>(cliente);
            outroId.put("identificacao", "DEMO-02");
            String statusOutroId = post("/api/vip/status", outroId).body();
            conferir(statusOutroId.contains("\"identificacaoDiferente\":true") && statusOutroId.contains("\"ativa\":false")
                    && !statusOutroId.contains(cliente.get("identificacao")), "Checkout avisa sobre identificação divergente");
            conferir(post("/api/pix/gerar", outroId).body().contains("\"valor\":129.90"),
                    "A identificação divergente recebe a regra de cliente padrão");
            cliente.put("email", "TESTE@EXEMPLO.COM");
            post("/api/carrinho/quantidade", Map.of("codigo", "SUP01", "quantidade", "6"));
            HttpResponse<String> pixAcimaDoLimite = post("/api/pix/gerar", cliente);
            conferir(pixAcimaDoLimite.statusCode() == 200 && pixAcimaDoLimite.body().contains("\"valor\":662.49"),
                    "Cobrança Pix VIP aplica 15% ao subtotal de R$ 779,40");
            post("/api/carrinho/quantidade", Map.of("codigo", "SUP01", "quantidade", "1"));
            HttpResponse<String> pixCompra = post("/api/pix/gerar", cliente);
            conferir(pixCompra.statusCode() == 200 && pixCompra.body().contains("\"valor\":116.91"),
                    "Identificação e e-mail do VIP ativo liberam desconto na compra");
            agora.set(vencimento);
            conferir(post("/api/vip/status", cliente).body().contains("\"ativa\":false"),
                    "O plano vence no instante registrado");
            conferir(post("/api/pix/confirmar", Map.of("referencia", campoJson(pixCompra.body(), "referencia")))
                    .statusCode() == 400, "O Java revalida a assinatura ao confirmar o Pix da compra");
            conferir(post("/api/pix/gerar", cliente).body().contains("\"valor\":129.90"),
                    "Após vencer, a compra volta ao preço padrão");

            agora.set(Instant.parse("2026-03-02T15:00:00Z"));
            mensalidade = post("/api/vip/pix/gerar", cliente);
            confirmar = Map.of("referencia", campoJson(mensalidade.body(), "referencia"));
            pagamento = post("/api/vip/pix/confirmar", confirmar);
            conferir(campoJson(pagamento.body(), "validaAte").equals("2026-04-02T15:00:00Z"),
                    "Após vencimento, o novo mês começa no dia do pagamento");
            mensalidade = post("/api/vip/pix/gerar", cliente);
            confirmar = Map.of("referencia", campoJson(mensalidade.body(), "referencia"));
            pagamento = post("/api/vip/pix/confirmar", confirmar);
            conferir(campoJson(pagamento.body(), "validaAte").equals("2026-05-02T15:00:00Z"),
                    "Renovação antecipada soma um mês à validade anterior");
        } finally {
            servidor.parar();
        }
        Servidor reaberto = new Servidor(0, dados, relogio);
        reaberto.iniciar();
        try {
            base = "http://127.0.0.1:" + reaberto.getPorta();
            String assinatura = post("/api/vip/status", cliente).body();
            conferir(assinatura.contains("\"ativa\":true")
                    && campoJson(assinatura, "validaAte").equals("2026-05-02T15:00:00Z"),
                    "Assinatura e validade persistem ao reiniciar");
        } finally {
            reaberto.parar();
            Files.deleteIfExists(dados);
            Files.deleteIfExists(pasta);
        }
    }
}
