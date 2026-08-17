package justfatlard.loot_ender;

/**
 * A container you can take from and not put into.
 *
 * <p>A marker rather than a check, because the thing enforcing it is a mixin on
 * every slot in the game and the cheapest question to ask there is what type
 * this is. Both shapes a loot copy comes in carry it: the single container and
 * the pair joined for a double chest.
 */
public interface TakeOnly {
}
