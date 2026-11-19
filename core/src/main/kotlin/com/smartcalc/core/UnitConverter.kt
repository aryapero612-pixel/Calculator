package com.smartcalc.core

enum class UnitCategory { LENGTH, MASS, VOLUME, AREA, SPEED, TIME, DATA, TEMPERATURE }

data class UnitDef(
    val name: String,
    val symbol: String,
    val category: UnitCategory,
    /** Multiply by this to get the base unit (not used for temperature). */
    val factor: Double,
    val aliases: List<String>,
)

object UnitConverter {
    private fun u(name: String, symbol: String, cat: UnitCategory, factor: Double, vararg aliases: String) =
        UnitDef(name, symbol, cat, factor, aliases.toList() + name.lowercase() + symbol.lowercase())

    val units: List<UnitDef> = listOf(
        // Length (base: metre)
        u("Millimetre", "mm", UnitCategory.LENGTH, 0.001, "millimeter", "millimeters", "millimetres"),
        u("Centimetre", "cm", UnitCategory.LENGTH, 0.01, "centimeter", "centimeters", "centimetres"),
        u("Metre", "m", UnitCategory.LENGTH, 1.0, "meter", "meters", "metres"),
        u("Kilometre", "km", UnitCategory.LENGTH, 1000.0, "kilometer", "kilometers", "kilometres", "kms"),
        u("Inch", "in", UnitCategory.LENGTH, 0.0254, "inches", "\""),
        u("Foot", "ft", UnitCategory.LENGTH, 0.3048, "feet", "'"),
        u("Yard", "yd", UnitCategory.LENGTH, 0.9144, "yards"),
        u("Mile", "mi", UnitCategory.LENGTH, 1609.344, "miles"),
        u("Nautical mile", "nmi", UnitCategory.LENGTH, 1852.0, "nautical miles"),
        // Mass (base: kilogram)
        u("Milligram", "mg", UnitCategory.MASS, 1e-6, "milligrams"),
        u("Gram", "g", UnitCategory.MASS, 0.001, "grams", "gm", "gms"),
        u("Kilogram", "kg", UnitCategory.MASS, 1.0, "kilograms", "kgs", "kilo", "kilos"),
        u("Tonne", "t", UnitCategory.MASS, 1000.0, "tonnes", "ton", "tons"),
        u("Pound", "lb", UnitCategory.MASS, 0.45359237, "pounds", "lbs"),
        u("Ounce", "oz", UnitCategory.MASS, 0.028349523125, "ounces"),
        // Volume (base: litre)
        u("Millilitre", "ml", UnitCategory.VOLUME, 0.001, "milliliter", "milliliters", "millilitres"),
        u("Litre", "l", UnitCategory.VOLUME, 1.0, "liter", "liters", "litres", "ltr"),
        u("Gallon (US)", "gal", UnitCategory.VOLUME, 3.785411784, "gallon", "gallons"),
        u("Cup (US)", "cup", UnitCategory.VOLUME, 0.2365882365, "cups"),
        u("Cubic metre", "m3", UnitCategory.VOLUME, 1000.0, "cubic meter", "cubic meters"),
        // Area (base: m²)
        u("Square metre", "m2", UnitCategory.AREA, 1.0, "sq m", "square meter", "square meters", "sqm"),
        u("Square foot", "ft2", UnitCategory.AREA, 0.09290304, "sq ft", "square feet", "sqft"),
        u("Square kilometre", "km2", UnitCategory.AREA, 1e6, "sq km", "square kilometer", "square kilometers"),
        u("Acre", "ac", UnitCategory.AREA, 4046.8564224, "acres"),
        u("Hectare", "ha", UnitCategory.AREA, 10000.0, "hectares"),
        // Speed (base: m/s)
        u("Metres/second", "m/s", UnitCategory.SPEED, 1.0, "mps"),
        u("Kilometres/hour", "km/h", UnitCategory.SPEED, 1 / 3.6, "kmh", "kph", "kmph"),
        u("Miles/hour", "mph", UnitCategory.SPEED, 0.44704, "mi/h"),
        u("Knot", "kn", UnitCategory.SPEED, 0.514444, "knots"),
        // Time (base: second)
        u("Second", "s", UnitCategory.TIME, 1.0, "sec", "secs", "seconds"),
        u("Minute", "min", UnitCategory.TIME, 60.0, "mins", "minutes"),
        u("Hour", "h", UnitCategory.TIME, 3600.0, "hr", "hrs", "hours"),
        u("Day", "d", UnitCategory.TIME, 86400.0, "days"),
        u("Week", "wk", UnitCategory.TIME, 604800.0, "weeks"),
        u("Year", "yr", UnitCategory.TIME, 31557600.0, "years", "yrs"),
        // Data (base: byte)
        u("Bit", "bit", UnitCategory.DATA, 0.125, "bits"),
        u("Byte", "B", UnitCategory.DATA, 1.0, "bytes"),
        u("Kilobyte", "KB", UnitCategory.DATA, 1024.0, "kilobytes"),
        u("Megabyte", "MB", UnitCategory.DATA, 1024.0 * 1024, "megabytes"),
        u("Gigabyte", "GB", UnitCategory.DATA, 1024.0 * 1024 * 1024, "gigabytes"),
        u("Terabyte", "TB", UnitCategory.DATA, 1024.0 * 1024 * 1024 * 1024, "terabytes"),
        // Temperature (handled specially)
        u("Celsius", "°C", UnitCategory.TEMPERATURE, 1.0, "c", "celsius", "centigrade", "degc", "degrees celsius"),
        u("Fahrenheit", "°F", UnitCategory.TEMPERATURE, 1.0, "f", "fahrenheit", "degf", "degrees fahrenheit"),
        u("Kelvin", "K", UnitCategory.TEMPERATURE, 1.0, "kelvin", "kelvins"),
    )

    fun find(text: String): UnitDef? {
        val t = text.trim().lowercase().removeSuffix(".")
        return units.firstOrNull { it.aliases.contains(t) }
    }

    fun byCategory(cat: UnitCategory) = units.filter { it.category == cat }

    fun convert(value: Double, from: UnitDef, to: UnitDef): Double {
        if (from.category != to.category) {
            throw CalcException("Cannot convert ${from.name} to ${to.name}")
        }
        if (from.category == UnitCategory.TEMPERATURE) {
            val celsius = when (from.name) {
                "Celsius" -> value
                "Fahrenheit" -> (value - 32) * 5 / 9
                else -> value - 273.15
            }
            return when (to.name) {
                "Celsius" -> celsius
                "Fahrenheit" -> celsius * 9 / 5 + 32
                else -> celsius + 273.15
            }
        }
        return value * from.factor / to.factor
    }
}
