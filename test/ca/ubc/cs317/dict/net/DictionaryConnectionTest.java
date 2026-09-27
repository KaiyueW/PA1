package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DictionaryConnectionTest {
    @Test
    public void testBasicConnection() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        assertNotNull(conn);
    }

    @Test
    public void testBasicDisconnect1() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        assertNotNull(conn);
        conn.close();
    }

    @Test
    public void testGetDatabaseList() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Map<String, Database> dbl = conn.getDatabaseList();
        assertTrue(dbl.size() > 0);
    }

    @Test
    public void testGetDefinition() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Map<String, Database> dbl = conn.getDatabaseList();
        assertTrue(dbl.size() > 0);
        Database wn = dbl.get("wn");
        assertNotNull(wn);
        Collection<Definition> defs = conn.getDefinitions("parrot", wn);
        assertTrue(defs.size() > 0);
    }


    @Test
    public void testGetDefinitionWildCardDict() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");

        Database wildcard = new Database("*", "all dbs");

        Collection<Definition> defs = conn.getDefinitions("bird", wildcard);

        // wildcard will DEFINITELY contain WN
        boolean containedWN = false;
        for (Definition def : defs) {
            if (def.getDatabaseName().equals("wn")) {
                containedWN = true;
                break;
            }
        }
        assertTrue(containedWN);
    }

    @Test
    public void testGetDefinitionSingularDict() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");

        Database wildcard = new Database("!", "singular db");

        Collection<Definition> defs = conn.getDefinitions("the", wildcard);

        assertTrue(defs.size() > 0);
    }

    @Test
    public void testGetStrategyListCommon() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Set<MatchingStrategy> strats = conn.getStrategyList();

        // NOTE: matchingstrategy compares equals by name so this works
        assertTrue(strats.contains(new MatchingStrategy("prefix", null)));
        assertTrue(strats.contains(new MatchingStrategy("exact", null)));
        assertTrue(strats.contains(new MatchingStrategy("first", null)));
        assertTrue(strats.contains(new MatchingStrategy("last", null)));
    }

    @Test
    public void testGetStrategyListWellFormed() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Set<MatchingStrategy> strats = conn.getStrategyList();

        for (MatchingStrategy matchingStrategy : strats) {
            assertNotNull(matchingStrategy.getName());
            assertNotNull(matchingStrategy.getDescription());
        }
    }

    @Test
    public void testGetMatchListWildcard() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Set<String> matches = conn.getMatchList("bird", new MatchingStrategy("exact", null), new Database("*", null));
        assertTrue(matches.size() > 0);
    }

    @Test
    public void testGetMatchListSingle() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Set<String> matches = conn.getMatchList("bird", new MatchingStrategy("exact", null), new Database("!", null));
        assertTrue(matches.size() > 0);
    }

    @Test
    public void testGetMatchListWildcardVariousStrats() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Set<String> matches1 = conn.getMatchList("bird", new MatchingStrategy("exact", null), new Database("*", null));
        assertTrue(matches1.size() > 0);
        Set<String> matches2 = conn.getMatchList("a", new MatchingStrategy("prefix", null), new Database("*", null));
        assertTrue(matches2.size() > 0);
        Set<String> matches3 = conn.getMatchList("e", new MatchingStrategy("last", null), new Database("*", null));
        assertTrue(matches3.size() > 0);
        Set<String> matches4 = conn.getMatchList("z", new MatchingStrategy("first", null), new Database("*", null));
        assertTrue(matches4.size() > 0);
    }


    @Test
    public void testGetDatabaseInfoCanHaveNewLines() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Map<String, Database> dbl = conn.getDatabaseList();

        // wn is a database that you SHOULD have and the description is multiple lines
        // long
        Database wndb = dbl.get("wn");
        assertTrue(conn.getDatabaseInfo(wndb).contains("\n"));
    }

    @Test
    public void testGetDatabaseInfo() throws DictConnectionException {
        DictionaryConnection conn = new DictionaryConnection("dict.org");
        Map<String, Database> dbMap = conn.getDatabaseList();
        Database wn = dbMap.get("wn");
        assertNotNull(wn, "WordNet database (wn) should be available on dict.org");
        String info = conn.getDatabaseInfo(wn);
        assertNotNull(info, "Database info string should not be null");
        assertTrue(info.length() > 0, "Database info string should not be empty");
        conn.close();
    }

}
