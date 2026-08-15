# Revoke our advancement so it can fire again next time a golden apple is eaten
advancement revoke @s only unbeta-content:golden_apple_health_gain
# Then trigger Attrition's health gain exactly as the enchanted golden apple would
function mp.attr:health_gain/landing
