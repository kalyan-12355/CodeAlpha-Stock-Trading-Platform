import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Stock Trading Platform Simulation (OOP + file persistence via serialization). */

public class StockTradingPlatform {
    private static final String DATA_FILE = "portfolio_data.ser";

    private Market market = new Market();
    private User user;
    private final Scanner in = new Scanner(System.in);

    private void save() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            out.writeObject(market);
            out.writeObject(user);
        } catch (IOException e) {
            System.out.println("Could not save data: " + e.getMessage());
        }
    }

    private boolean load() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return false;
        try (ObjectInputStream oin = new ObjectInputStream(new FileInputStream(f))) {
            market = (Market) oin.readObject();
            user = (User) oin.readObject();
            return true;
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Could not load saved data, starting fresh.");
            return false;
        }
    }

    private String prompt(String msg) {
        System.out.print(msg);
        return in.hasNextLine() ? in.nextLine().trim() : "8"; // EOF -> exit
    }

    private void trade(boolean isBuy) {
        Stock stock = market.get(prompt("Symbol: "));
        if (stock == null) {
            System.out.println("Unknown symbol.");
            return;
        }
        try {
            int qty = Integer.parseInt(prompt("Quantity: "));
            if (isBuy) user.buy(stock, qty); else user.sell(stock, qty);
            user.recordSnapshot(market);
            System.out.printf("%s successful: %d x %s @ %.2f%n",
                    isBuy ? "BUY" : "SELL", qty, stock.getSymbol(), stock.getPrice());
        } catch (NumberFormatException e) {
            System.out.println("Error: enter a whole number.");
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void start() {
        if (load()) {
            System.out.println("Welcome back, " + user.getName() + "! Data loaded.");
        } else {
            String name = prompt("Enter your name: ");
            user = new User(name.isEmpty() ? "Trader" : name);
            user.recordSnapshot(market);
            System.out.printf("Account created with $%,.2f virtual cash.%n", User.STARTING_CASH);
        }
        menu();
    }

    private void menu() {
        while (true) {
            System.out.println("\n=== Stock Trading Platform ===");
            System.out.println("1. View market data      5. Portfolio performance");
            System.out.println("2. Buy stock             6. Transaction history");
            System.out.println("3. Sell stock            7. Advance to next day");
            System.out.println("4. View portfolio        8. Save & exit");
            switch (prompt("Choose: ")) {
                case "1": market.display(); break;
                case "2": market.display(); trade(true); break;
                case "3": trade(false); break;
                case "4": user.showPortfolio(market); break;
                case "5": user.showPerformance(); break;
                case "6":
                    System.out.println("\n--- Transactions ---");
                    if (user.getTransactions().isEmpty()) System.out.println("No transactions yet.");
                    for (Transaction t : user.getTransactions()) System.out.println(t);
                    break;
                case "7":
                    market.nextDay();
                    user.recordSnapshot(market);
                    System.out.println("Moved to day " + market.getDay() + ".");
                    market.display();
                    break;
                case "8":
                    save();
                    System.out.println("Data saved. Goodbye!");
                    return;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    public static void main(String[] args) {
        new StockTradingPlatform().start();
    }
}

class Stock implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Random RNG = new Random();

    private final String symbol, name;
    private double price, prevPrice;
    private final List<Double> history = new ArrayList<>();

    Stock(String symbol, String name, double price) {
        this.symbol = symbol;
        this.name = name;
        this.price = this.prevPrice = price;
        history.add(price);
    }

    /** Random-walk price change of about -5% to +5%. */
    void updatePrice() {
        prevPrice = price;
        double change = (RNG.nextDouble() * 0.10) - 0.05;
        price = Math.max(1.0, Math.round(price * (1 + change) * 100.0) / 100.0);
        history.add(price);
    }

    String getSymbol() { return symbol; }
    String getName() { return name; }
    double getPrice() { return price; }
    double getChangePct() { return (price - prevPrice) / prevPrice * 100; }
}

class Market implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Stock> stocks = new LinkedHashMap<>();
    private int day = 0;

    Market() {
        add(new Stock("AAPL", "Apple Inc.", 180));
        add(new Stock("GOOG", "Alphabet Inc.", 140));
        add(new Stock("MSFT", "Microsoft Corp.", 370));
        add(new Stock("TSLA", "Tesla Inc.", 240));
        add(new Stock("AMZN", "Amazon.com Inc.", 150));
        add(new Stock("INFY", "Infosys Ltd.", 18));
    }

    private void add(Stock s) { stocks.put(s.getSymbol(), s); }

    Stock get(String symbol) { return stocks.get(symbol.toUpperCase()); }
    int getDay() { return day; }

    void nextDay() {
        day++;
        for (Stock s : stocks.values()) s.updatePrice();
    }

    void display() {
        System.out.println("\n--- Market Data (Day " + day + ") ---");
        System.out.printf("%-8s%-20s%10s%10s%n", "Symbol", "Name", "Price", "Change");
        for (Stock s : stocks.values()) {
            System.out.printf("%-8s%-20s%10.2f%9.2f%%%n",
                    s.getSymbol(), s.getName(), s.getPrice(), s.getChangePct());
        }
    }
}

class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String type, symbol, timestamp;
    private final int qty;
    private final double price;

    Transaction(String type, String symbol, int qty, double price) {
        this.type = type;
        this.symbol = symbol;
        this.qty = qty;
        this.price = price;
        this.timestamp = LocalDateTime.now().format(FMT);
    }

    @Override
    public String toString() {
        return String.format("%s  %-4s %-6s %4d @ %8.2f = %10.2f",
                timestamp, type, symbol, qty, price, qty * price);
    }
}

class User implements Serializable {
    private static final long serialVersionUID = 1L;
    static final double STARTING_CASH = 10000.0;

    private final String name;
    private double cash = STARTING_CASH;
    private final Map<String, Integer> holdings = new LinkedHashMap<>();
    private final Map<String, Double> avgCost = new HashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<double[]> snapshots = new ArrayList<>(); // {day, totalValue}

    User(String name) { this.name = name; }

    String getName() { return name; }
    List<Transaction> getTransactions() { return transactions; }

    void buy(Stock stock, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        double cost = stock.getPrice() * qty;
        if (cost > cash)
            throw new IllegalArgumentException(
                    String.format("Insufficient funds. Need %.2f, have %.2f.", cost, cash));
        int held = holdings.getOrDefault(stock.getSymbol(), 0);
        double oldCost = avgCost.getOrDefault(stock.getSymbol(), 0.0) * held;
        holdings.put(stock.getSymbol(), held + qty);
        avgCost.put(stock.getSymbol(), (oldCost + cost) / (held + qty));
        cash -= cost;
        transactions.add(new Transaction("BUY", stock.getSymbol(), qty, stock.getPrice()));
    }

    void sell(Stock stock, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        int held = holdings.getOrDefault(stock.getSymbol(), 0);
        if (qty > held)
            throw new IllegalArgumentException(
                    "You only hold " + held + " share(s) of " + stock.getSymbol() + ".");
        if (held == qty) {
            holdings.remove(stock.getSymbol());
            avgCost.remove(stock.getSymbol());
        } else {
            holdings.put(stock.getSymbol(), held - qty);
        }
        cash += stock.getPrice() * qty;
        transactions.add(new Transaction("SELL", stock.getSymbol(), qty, stock.getPrice()));
    }

    double holdingsValue(Market market) {
        double total = 0;
        for (Map.Entry<String, Integer> e : holdings.entrySet())
            total += market.get(e.getKey()).getPrice() * e.getValue();
        return total;
    }

    double totalValue(Market market) { return cash + holdingsValue(market); }

    void recordSnapshot(Market market) {
        double value = Math.round(totalValue(market) * 100.0) / 100.0;
        double[] snap = {market.getDay(), value};
        if (!snapshots.isEmpty() && snapshots.get(snapshots.size() - 1)[0] == market.getDay())
            snapshots.set(snapshots.size() - 1, snap);
        else
            snapshots.add(snap);
    }

    void showPortfolio(Market market) {
        System.out.println("\n--- Portfolio: " + name + " ---");
        System.out.printf("%-8s%5s%11s%10s%12s%11s%n",
                "Symbol", "Qty", "Avg Cost", "Price", "Value", "P/L");
        for (Map.Entry<String, Integer> e : holdings.entrySet()) {
            String sym = e.getKey();
            int qty = e.getValue();
            double price = market.get(sym).getPrice();
            double pl = (price - avgCost.get(sym)) * qty;
            System.out.printf("%-8s%5d%11.2f%10.2f%12.2f%11.2f%n",
                    sym, qty, avgCost.get(sym), price, price * qty, pl);
        }
        double total = totalValue(market);
        double pl = total - STARTING_CASH;
        System.out.printf("%nCash: %.2f | Holdings: %.2f | Total: %.2f%n",
                cash, holdingsValue(market), total);
        System.out.printf("Overall P/L: %+.2f (%+.2f%%)%n", pl, pl / STARTING_CASH * 100);
    }

    void showPerformance() {
        System.out.println("\n--- Portfolio Performance Over Time ---");
        if (snapshots.isEmpty()) {
            System.out.println("No history yet.");
            return;
        }
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[] s : snapshots) {
            lo = Math.min(lo, s[1]);
            hi = Math.max(hi, s[1]);
        }
        double span = (hi - lo) == 0 ? 1 : (hi - lo);
        for (double[] s : snapshots) {
            int bars = 1 + (int) ((s[1] - lo) / span * 30);
            System.out.printf("Day %3d | %10.2f | %s%n", (int) s[0], s[1], "#".repeat(bars));
        }
        System.out.printf("Best: %.2f | Worst: %.2f | Latest: %.2f%n",
                hi, lo, snapshots.get(snapshots.size() - 1)[1]);
    }
}
