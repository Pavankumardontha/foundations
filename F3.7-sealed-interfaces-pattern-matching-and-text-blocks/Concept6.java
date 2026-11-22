public class Concept6 {

    public static void main(String[] args) {
        // OLD WORLD: one logical string, escaped quotes, \n by hand, + concatenation.
        String sqlOld = "SELECT id, name FROM events\n"
                      + " WHERE starts_at > now()\n"
                      + " ORDER BY starts_at";

        String jsonOld = "{\n"
                       + "  \"eventId\": \"E-7\",\n"
                       + "  \"seats\": [\"A1\", \"A2\"]\n"
                       + "}";

        // NEW WORLD: text blocks — what you see is what you get.
        String sql = """
                SELECT id, name FROM events
                 WHERE starts_at > now()
                 ORDER BY starts_at""";

        String json = """
                {
                  "eventId": "E-7",
                  "seats": ["A1", "A2"]
                }""";

        System.out.println("--- sql ---");
        System.out.println(sql);
        System.out.println("--- json ---");
        System.out.println(json);
        System.out.println("old sql  equals new? " + sqlOld.equals(sql));
        System.out.println("old json equals new? " + jsonOld.equals(json));
    }
}
