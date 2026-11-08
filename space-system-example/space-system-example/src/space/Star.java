package space;

/** A sun. Extra suns in binary/trinary systems orbit the system centre. */
public record Star(String name, StarClass starClass, double radius, Orbit orbit) {}
