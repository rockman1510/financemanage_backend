package com.financemanage.ledger.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The categories every user starts with (similar to Money Lover's defaults). */
public final class DefaultCategories {

    private record Seed(String code, String name, CategoryType type, String icon, List<Seed> children) {

        static Seed of(String code, String name, CategoryType type, String icon, Seed... children) {
            return new Seed(code, name, type, icon, List.of(children));
        }
    }

    private static final CategoryType IN = CategoryType.INCOME;
    private static final CategoryType OUT = CategoryType.EXPENSE;

    private static final List<Seed> SEEDS = List.of(
            Seed.of("FOOD", "Ăn uống", OUT, "food",
                    Seed.of("FOOD_RESTAURANT", "Nhà hàng", OUT, "restaurant"),
                    Seed.of("FOOD_COFFEE", "Cà phê", OUT, "coffee")),
            Seed.of("BILLS", "Hoá đơn & Tiện ích", OUT, "bill",
                    Seed.of("BILLS_ELECTRICITY", "Điện", OUT, "electricity"),
                    Seed.of("BILLS_WATER", "Nước", OUT, "water"),
                    Seed.of("BILLS_INTERNET", "Internet", OUT, "internet"),
                    Seed.of("BILLS_PHONE", "Điện thoại", OUT, "phone"),
                    Seed.of("BILLS_RENT", "Thuê nhà", OUT, "house")),
            Seed.of("TRANSPORT", "Di chuyển", OUT, "transport",
                    Seed.of("TRANSPORT_FUEL", "Xăng xe", OUT, "fuel"),
                    Seed.of("TRANSPORT_PARKING", "Gửi xe", OUT, "parking"),
                    Seed.of("TRANSPORT_TAXI", "Taxi", OUT, "taxi")),
            Seed.of("SHOPPING", "Mua sắm", OUT, "shopping",
                    Seed.of("SHOPPING_CLOTHES", "Quần áo", OUT, "clothes"),
                    Seed.of("SHOPPING_HOUSEHOLD", "Đồ gia dụng", OUT, "household")),
            Seed.of("ENTERTAINMENT", "Giải trí", OUT, "entertainment"),
            Seed.of("HEALTH", "Sức khoẻ", OUT, "health"),
            Seed.of("EDUCATION", "Giáo dục", OUT, "education"),
            Seed.of("FAMILY", "Gia đình", OUT, "family"),
            Seed.of("FRIENDS", "Bạn bè & Người yêu", OUT, "friends"),
            Seed.of("GIFTS_DONATIONS", "Quà tặng & Quyên góp", OUT, "gift"),
            Seed.of("INSURANCE", "Bảo hiểm", OUT, "insurance"),
            Seed.of("FEES", "Phí & Lệ phí", OUT, "fee"),
            Seed.of("OTHER_EXPENSE", "Chi phí khác", OUT, "other"),
            Seed.of("SALARY", "Lương", IN, "salary"),
            Seed.of("BONUS", "Thưởng", IN, "bonus"),
            Seed.of("INTEREST", "Tiền lãi", IN, "interest"),
            Seed.of("SELLING", "Bán đồ", IN, "selling"),
            Seed.of("GIFTS_RECEIVED", "Được tặng", IN, "gift"),
            Seed.of("OTHER_INCOME", "Thu nhập khác", IN, "other"));

    private DefaultCategories() {
    }

    /** Fresh category instances (parents before their children) for a new owner. */
    public static List<Category> forOwner(UUID ownerId) {
        List<Category> categories = new ArrayList<>();
        for (Seed seed : SEEDS) {
            Category parent = Category.seeded(ownerId, seed.code(), seed.name(), seed.type(), null, seed.icon());
            categories.add(parent);
            for (Seed child : seed.children()) {
                categories.add(Category.seeded(ownerId, child.code(), child.name(), child.type(), parent.getId(),
                        child.icon()));
            }
        }
        return categories;
    }
}
