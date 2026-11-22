interface PriceSource {
    int priceFor(String item);
}

class CatalogPriceSource implements PriceSource {
    @Override
    public int priceFor(String item) {
        // Pretend this looks the price up in a database.
        return 500;
    }
}

class FixedPriceSource implements PriceSource {
    @Override
    public int priceFor(String item) {
        return 100;
    }
}

class CheckoutService {
    private final PriceSource prices;

    CheckoutService(PriceSource prices) {
        this.prices = prices;
    }

    int totalFor(String item, int quantity) {
        return prices.priceFor(item) * quantity;
    }
}

public class Q1L3 {
    public static void main(String[] args) {
        CheckoutService checkout = new CheckoutService(new FixedPriceSource());
        System.out.println(checkout.totalFor("ticket", 3));
    }
}
