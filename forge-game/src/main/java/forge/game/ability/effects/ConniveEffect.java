package forge.game.ability.effects;

import com.google.common.collect.Maps;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameEntityCounterTable;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.*;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConniveEffect extends SpellAbilityEffect {

    /* (non-Javadoc)
     * @see forge.game.ability.SpellAbilityEffect#getStackDescription(forge.game.spellability.SpellAbility)
     * returns the automatically generated stack description string
     */
    @Override
    protected String getStackDescription(SpellAbility sa) {
        List<Card> tgt = getTargetCards(sa);
        if (tgt.size() <= 0) {
            return "";
        } else {
        final StringBuilder sb = new StringBuilder();
        sb.append(Lang.joinHomogenous(tgt)).append(tgt.size() > 1 ? " connive." : " connives.");
        return sb.toString();
        }
    }

    /* (non-Javadoc)
     * @see forge.game.ability.SpellAbilityEffect#resolve(forge.game.spellability.SpellAbility)
     */
    @Override
    public void resolve(SpellAbility sa) {
        final Card host = sa.getHostCard();
        final Player hostCon = host.getController();
        final Game game = host.getGame();
        final int num = AbilityUtils.calculateAmount(host, sa.getParamOrDefault("ConniveNum", "1"), sa);

        GameEntityCounterTable table = new GameEntityCounterTable();
        final CardZoneTable triggerList = new CardZoneTable();
        List<Card> allToDiscard = new ArrayList<>();
        Map<Player, CardCollectionView> discardedMap = Maps.newHashMap();
        Map<AbilityKey, Object> moveParams = AbilityKey.newMap();
        moveParams.put(AbilityKey.LastStateBattlefield, sa.getLastStateBattlefield());
        moveParams.put(AbilityKey.LastStateGraveyard, sa.getLastStateGraveyard());

        CardCollection toConnive = getTargetCards(sa);
        Card conniver;
        while (toConnive.size() > 0) {
            if (toConnive.size() > 1) {
                conniver = hostCon.getController().chooseSingleEntityForEffect(toConnive, sa, "Choose conniver", null);
            } else {
                conniver = toConnive.get(0);
            }

            hostCon.drawCards(num, sa, moveParams);

            CardCollectionView dPHand = hostCon.getCardsIn(ZoneType.Hand);
            dPHand = CardLists.filter(dPHand, CardPredicates.Presets.NON_TOKEN);
            if (dPHand.isEmpty() || !hostCon.canDiscardBy(sa, true)) { // hand being empty unlikely, but just to be safe
                continue;
            }

            CardCollection validCards = CardLists.getValidCards(dPHand, "Card", hostCon, host, sa);
            validCards.removeAll(allToDiscard); //don't allow cards already chosen for discard by other connivers

            int amt = Math.min(validCards.size(), num);
            CardCollectionView toBeDiscarded = amt == 0 ? CardCollection.EMPTY :
                    hostCon.getController().chooseCardsToDiscardFrom(p, sa, validCards, amt, amt);

            if (toBeDiscarded.size() > 1) {
                toBeDiscarded = GameActionUtil.orderCardsByTheirOwners(game, toBeDiscarded, ZoneType.Graveyard, sa);
            }

            for (Card disc : toBeDiscarded) {
                allToDiscard.add(disc);
            }

            int numCntrs = CardLists.getValidCardCount(toBeDiscarded, "Card.nonLand", hostCon, host, sa);

            // need to get newest game state to check if it is still on the battlefield and the timestamp didn't change
            Card gamec = game.getCardState(conniver);
            // if the card is not in the game anymore, this might still return true, but it's no problem
            if (game.getZoneOf(gamec).is(ZoneType.Battlefield) && gamec.equalsWithTimestamp(conniver)) {
                conniver.addCounter(CounterEnumType.P1P1, numCntrs, hostCon, table);
            }
            toConnive.remove(conniver);
        }

        if (allToDiscard.size() > 0) {
            discardedMap.put(hostCon, CardCollection.getView(allToDiscard));
            discard(sa, triggerList, true, discardedMap, moveParams);
            table.replaceCounterEffect(game, sa, true);
            triggerList.triggerChangesZoneAll(game, sa);
        }
    }
}
