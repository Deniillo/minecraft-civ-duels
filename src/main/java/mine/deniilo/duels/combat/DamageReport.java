package mine.deniilo.duels.combat;

import java.util.Locale;

public final class DamageReport {

    private DamageReport() {
    }

    public static String describe(AttackInput in, DamageBreakdown b) {
        StringBuilder sb = new StringBuilder();
        sb.append(typeName(in.type()));
        if (in.type() == AttackType.MELEE) {
            sb.append(" s=").append(num(in.strength()));
        } else if (in.type() == AttackType.BOW) {
            sb.append(" f=").append(num(in.strength()));
        }
        sb.append(" B=").append(num(in.base()))
                .append(" E=").append(in.attackLevel())
                .append(" | A=").append(num(b.attack()))
                .append(" P=").append(num(b.piercing()))
                .append(" D=").append(num(b.defense()))
                .append(" C=").append(num(b.critCoefficient()))
                .append(" | H0=").append(num(b.beforeSoul()));
        if (b.soulHalved()) {
            sb.append(" SOUL/2");
        }
        sb.append(" => ").append(num(b.finalDamage())).append(" HP");
        return sb.toString();
    }

    static String typeName(AttackType type) {
        return switch (type) {
            case MELEE -> "ближний";
            case THROWN_TRIDENT -> "бросок";
            case BOW -> "лук";
            case CROSSBOW -> "арбалет";
            case FIST -> "кулак";
        };
    }

    static String num(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
