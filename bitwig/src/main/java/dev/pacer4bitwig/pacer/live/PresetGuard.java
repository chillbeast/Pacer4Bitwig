// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;


/**
 * Is the Pacer still on the extension's preset? Live writes go to preset index 0 - whatever preset is loaded - so
 * after the Pacer was switched to another preset they would recolour that one. The guard reads the loaded preset's
 * name back (a GET, read-only) and compares it with the names the extension wrote: the same means ours, the stored
 * name of the Bitwig preset means ours but freshly loaded (every live edit gone), a name only the extension leaves
 * behind (`OFF`, a mode's name, `ROW 3` - an earlier session's) means ours, anything else means another preset. A
 * press on the Bitwig preset's switches, jacks or pedals is proof too: other presets keep off its channel.
 * <p>
 * It asks before writing when its last answer is getting old, and - with the heartbeat - every few seconds, which also
 * notices a Pacer that was unplugged and came back. A name write needs a fresher answer than a colour: written into
 * another preset, the name would make that preset look like ours. Nothing is written before the first answer; a Pacer
 * that never answers is then treated as if there were no guard, so a firmware that ignores the question costs a
 * second at startup and the question now and then. Pure: times are passed in.
 */
public final class PresetGuard
{
    /** Where the guard stands. */
    public enum State
    {
        /** No answer yet: writes wait for the first one (or for giving up on it). */
        UNKNOWN,
        /** The extension's preset is loaded. */
        OURS,
        /** Another preset is loaded: nothing is written until ours is back. */
        FOREIGN,
        /** The Pacer has answered before and stopped: it is unplugged or off. */
        ABSENT,
        /** The Pacer has never answered: writes go out, as without the guard. */
        UNANSWERED
    }


    /** What an answer means for the caller. */
    public enum Verdict
    {
        /** Nothing to do. */
        NONE,
        /** Paint everything again: the Pacer came back, or reloaded the stored preset. */
        REPAINT,
        /** Another preset was just found loaded. */
        FOREIGN
    }


    /** An answer older than this is asked again before a write. */
    public static final long    CONFIRM_MILLIS    = 1500;
    /** A name write needs an answer this fresh. */
    public static final long    NAME_CONFIRM_MILLIS = 600;
    /** No answer by then counts as none. */
    public static final long    TIMEOUT_MILLIS    = 1000;
    /** A Pacer that answered before counts as gone after this many questions in a row went unanswered. */
    public static final int     MISSES_TO_ABSENT  = 2;
    /** How often the heartbeat asks. */
    public static final long    HEARTBEAT_MILLIS  = 5000;
    /** How often an absent Pacer is asked, to notice it coming back. */
    public static final long    RETRY_MILLIS      = 3000;
    /** How often a Pacer that never answered is asked again. */
    public static final long    UNANSWERED_MILLIS = 10000;
    /** Never two questions closer together than this. */
    public static final long    MIN_GAP_MILLIS    = 300;
    /** Right after a name write the Pacer can still answer with the name before it (~250 ms measured). */
    public static final long    STALE_READ_MILLIS = 1000;

    private static final long   NEVER             = Long.MIN_VALUE;
    private static final int    NAMES_KEPT        = 8;

    private final String        storedName;
    /** Names only the extension leaves on the display, as keys ({@link #key(String)}). */
    private final Predicate<String> ownNames;
    private State               state             = State.UNKNOWN;
    private boolean             everAnswered;
    private int                 misses;
    private long                confirmedAt       = NEVER;
    private long                probeSentAt       = NEVER;
    private long                lastProbeAt       = NEVER;
    private long                lastNameWriteAt   = NEVER;
    private String              currentName;
    /** Recently written names with the time they went out, oldest first. */
    private final Map<String, Long> writtenNames  = new LinkedHashMap<> ();


    /**
     * Constructor.
     *
     * @param storedName The name the extension's preset has in the Pacer's memory
     */
    public PresetGuard (final String storedName)
    {
        this (storedName, name -> false);
    }


    /**
     * Constructor.
     *
     * @param storedName The name the extension's preset has in the Pacer's memory
     * @param ownNames Names only the extension leaves on the display (an earlier session's), given as keys: five
     *            characters, space padded, upper case
     */
    public PresetGuard (final String storedName, final Predicate<String> ownNames)
    {
        this.storedName = key (storedName);
        this.ownNames = ownNames;
    }


    /**
     * How names are compared: as the Pacer stores them (five characters, 7-bit), ignoring case.
     *
     * @param name A name
     * @return The key
     */
    public static String key (final String name)
    {
        return PacerSysex.pad (name).toUpperCase (Locale.ROOT);
    }


    /**
     * @return Where the guard stands
     */
    public State getState ()
    {
        return this.state;
    }


    /**
     * @param now Milliseconds
     * @return True if live writes may go out now
     */
    public boolean mayWrite (final long now)
    {
        return this.mayWrite (now, CONFIRM_MILLIS);
    }


    /**
     * @param now Milliseconds
     * @return True if the display name may be written now
     */
    public boolean mayWriteName (final long now)
    {
        return this.mayWrite (now, NAME_CONFIRM_MILLIS);
    }


    private boolean mayWrite (final long now, final long fresh)
    {
        return switch (this.state)
        {
            case UNANSWERED -> true;
            case OURS -> now - this.confirmedAt <= fresh;
            case UNKNOWN, FOREIGN, ABSENT -> false;
        };
    }


    /**
     * @return True if the Pacer is known to be on another preset or gone - nothing should be written at all, not even
     *         the blackout on exit
     */
    public boolean isElsewhere ()
    {
        return this.state == State.FOREIGN || this.state == State.ABSENT;
    }


    /**
     * Should a question go out now?
     *
     * @param now Milliseconds
     * @param writeWaiting True if a write was held back since the last call
     * @param heartbeat True to ask regularly, also without writes
     * @return True to send one, then call {@link #probeSent(long)}
     */
    public boolean shouldProbe (final long now, final boolean writeWaiting, final boolean heartbeat)
    {
        if (this.probeSentAt != NEVER)
            return false;
        final long sinceProbe = this.lastProbeAt == NEVER ? Long.MAX_VALUE : now - this.lastProbeAt;
        if (sinceProbe < MIN_GAP_MILLIS)
            return false;
        return switch (this.state)
        {
            case UNKNOWN -> true;
            case OURS -> writeWaiting || heartbeat && now - this.confirmedAt > HEARTBEAT_MILLIS;
            // Coming back to the Bitwig preset announces itself (CC 119); the heartbeat covers a lost announcement
            case FOREIGN -> heartbeat && sinceProbe > HEARTBEAT_MILLIS;
            case ABSENT -> sinceProbe > RETRY_MILLIS;
            case UNANSWERED -> sinceProbe > UNANSWERED_MILLIS;
        };
    }


    /**
     * A question went out.
     *
     * @param now Milliseconds
     */
    public void probeSent (final long now)
    {
        this.probeSentAt = now;
        this.lastProbeAt = now;
    }


    /**
     * Give up on a question that was not answered in time.
     *
     * @param now Milliseconds
     * @return True if that changed the state
     */
    public boolean checkTimeout (final long now)
    {
        if (this.probeSentAt == NEVER || now - this.probeSentAt <= TIMEOUT_MILLIS)
            return false;
        this.probeSentAt = NEVER;
        final State before = this.state;
        // Silence from a Pacer that answered before means it is gone - once is a slow answer, twice is not. One that
        // never answered may simply not answer this question.
        if (!this.everAnswered)
            this.state = State.UNANSWERED;
        else if (++this.misses >= MISSES_TO_ABSENT)
            this.state = State.ABSENT;
        return this.state != before;
    }


    /**
     * The extension wrote the display name.
     *
     * @param name The name
     * @param now Milliseconds
     */
    public void nameWritten (final String name, final long now)
    {
        final String padded = key (name);
        this.currentName = padded;
        this.lastNameWriteAt = now;
        this.writtenNames.remove (padded);
        this.writtenNames.put (padded, Long.valueOf (now));
        final Iterator<String> oldest = this.writtenNames.keySet ().iterator ();
        while (this.writtenNames.size () > NAMES_KEPT && oldest.hasNext ())
        {
            oldest.next ();
            oldest.remove ();
        }
    }


    /**
     * The Bitwig preset announced itself (CC 119): it was just selected, so it is loaded.
     *
     * @param now Milliseconds
     */
    public void presetAnnounced (final long now)
    {
        this.confirm (now);
    }


    /**
     * A switch, jack or pedal of the Bitwig preset just sent something - other presets keep off its channel, so the
     * Bitwig preset is loaded.
     *
     * @param now Milliseconds
     * @return True if the board has to be painted again: the Pacer was taken for another preset, or for gone
     */
    public boolean controlUsed (final long now)
    {
        final boolean wasElsewhere = this.isElsewhere ();
        this.misses = 0;
        this.confirm (now);
        return wasElsewhere;
    }


    /**
     * The Pacer answered with the loaded preset's name.
     *
     * @param name The name
     * @param now Milliseconds
     * @return What it means
     */
    public Verdict answered (final String name, final long now)
    {
        // Another application's dump, not an answer to us
        if (this.probeSentAt == NEVER)
            return Verdict.NONE;
        final long sentAt = this.probeSentAt;
        this.probeSentAt = NEVER;
        this.everAnswered = true;
        this.misses = 0;

        final String padded = key (name);
        final State before = this.state;
        if (padded.equals (this.currentName) || this.writtenSince (padded, sentAt - STALE_READ_MILLIS) || this.ownNames.test (padded))
        {
            this.confirm (now);
            // Back from another preset or from being unplugged, the Pacer shows whatever it had: paint it all
            return before == State.FOREIGN || before == State.ABSENT ? Verdict.REPAINT : Verdict.NONE;
        }
        // Right after a name write the Pacer can still answer with the name before it
        final boolean justWrote = this.lastNameWriteAt != NEVER && sentAt - this.lastNameWriteAt < STALE_READ_MILLIS;
        if (padded.equals (this.storedName))
        {
            // The Bitwig preset, fresh from its stored copy: every live edit is gone - unless this answer merely
            // predates the first write (at startup, say), which is on its way
            this.confirm (now);
            return justWrote ? Verdict.NONE : Verdict.REPAINT;
        }
        // A name the extension never wrote, and not one that was about to be overwritten
        if (justWrote)
            return Verdict.NONE;
        this.state = State.FOREIGN;
        return before == State.FOREIGN ? Verdict.NONE : Verdict.FOREIGN;
    }


    private boolean writtenSince (final String name, final long since)
    {
        final Long at = this.writtenNames.get (name);
        return at != null && at.longValue () >= since;
    }


    private void confirm (final long now)
    {
        this.state = State.OURS;
        this.confirmedAt = now;
    }
}
