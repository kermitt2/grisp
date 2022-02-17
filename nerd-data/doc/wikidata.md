# Getting all Wikidata properties in JSON

At the present time (2022), we can obtain the list of all Wikidata properties for a target language with the following sparql query (it takes 5-10s): 

```bash
wget "https://query.wikidata.org/sparql?format=json&query=SELECT%20%3Fproperty%20%3FpropertyLabel%20WHERE%20%7B%0A%20%20%20%20%3Fproperty%20a%20wikibase%3AProperty%20.%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%0A%20%20%20%20%20%20bd%3AserviceParam%20wikibase%3Alanguage%20%22en%22%20.%0A%20%20%20%7D%0A%20%7D%0A%0A" -O en/wikidata-properties.json
```

Replacing the language code `en` (toward the end of this awful query) produces a list of properties into another target language.

Each property will follow this pattern:

```json
{
  "head" : {
    "vars" : [ "property", "propertyLabel" ]
  },
  "results" : {
    "bindings" : [ {
      "property" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/P6"
      },
      "propertyLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "head of government"
      }
    }, {
      "property" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/P10"
      },
      "propertyLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "video"
      }
    }, 
    ...
  }
}
```
