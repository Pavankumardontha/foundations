import java.time.Duration;
System.out.println("PT30S   -> " + Duration.parse("PT30S").getSeconds() + " seconds");
System.out.println("PT2M    -> " + Duration.parse("PT2M").getSeconds() + " seconds");
System.out.println("PT10M   -> " + Duration.parse("PT10M").getSeconds() + " seconds");
System.out.println("PT90S   -> " + Duration.parse("PT90S").getSeconds() + " seconds");
System.out.println("PT1M30S -> " + Duration.parse("PT1M30S").getSeconds() + " seconds");
System.out.println("PT1H30M -> " + Duration.parse("PT1H30M").toMinutes() + " minutes");
System.out.println("P1D     -> " + Duration.parse("P1D").toHours() + " hours");
System.out.println("P1DT12H -> " + Duration.parse("P1DT12H").toHours() + " hours");
System.out.println("pt10m   -> " + Duration.parse("pt10m").toMinutes() + " minutes (lowercase)");
try { Duration.parse("10"); } catch (Exception e) { System.out.println("10      -> " + e); }
try { Duration.parse("10m"); } catch (Exception e) { System.out.println("10m     -> " + e); }
try { Duration.parse("PT10"); } catch (Exception e) { System.out.println("PT10    -> " + e); }
try { Duration.parse("P1M"); } catch (Exception e) { System.out.println("P1M     -> " + e); }
/exit
