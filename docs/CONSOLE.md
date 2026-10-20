# Using the console

Start with `./run.sh` or `run.bat`. Type the number of a menu item and press Enter. `0` exits.

| Menu | What it does |
|------|--------------|
| 1 Search | Type part of a name, brand or chipset (`iphone 17`, `s25 ultra`, `snapdragon`). Spaces, case and `+` are handled. |
| 2 Filter | Answer the questions you care about, press Enter to skip the rest: name, brands (comma separated), max/min price (INR), min RAM/storage/battery/charging, screen size, refresh rate, main camera, year, 5G, NFC, wireless charging, OS, chipset, sort order. |
| 3 Browse by brand | Lists brands with their device counts, then the devices of the brand you type. |
| 4 Details | Type a name; if several match, pick one. Shows key specs and every listed specification. |
| 5 Compare | Add 2 to 4 devices by name (`a`), remove (`r 2`), compare (`c`), clear (`x`). Also add from any result list with `c <number>`. |
| 6 Assistant | Describe what you want ("gaming phone under 30000 with 8 GB RAM"). Refine in later messages ("only Samsung"). `new` starts over, `b` returns. |
| 7 Top lists | Biggest battery, most RAM, highest camera megapixels, lightest, fastest charging, cheapest, newest; optional price limit. Only phones that state the value are ranked. |
| 8 My shortlist | Phones you saved with `s`. Stored in `teqzo-shortlist.txt` (change with `TEQZO_SHORTLIST`), so it survives restarts. Open, remove, compare all (2-4), export to CSV. |
| 9 Market insights | Phones and average price/battery/RAM per brand, phones per year and price-band histogram, computed in the database with GROUP BY. |
| 10 About | Counts per data source and whether an AI model is configured. |

Result lists show 10 devices per page: `n` next, `p` previous, a number opens details, `c <number>` adds to the
compare list, `s <number>` saves to the shortlist, `x` exports all matches (up to 500) to a CSV file next to the
program, `b` goes back. On a details page: `c` compare, `s` shortlist, `l` similar phones. Details also show the
launch prices by country (as listed, never converted) when the Mobiles 2025 data has them. A comparison ends with a
verdict that counts in how many comparable rows each phone leads. Sort keys for the filter menu are listed at the prompt.
Errors (invalid input, database down) are shown as plain messages; the program keeps running.
