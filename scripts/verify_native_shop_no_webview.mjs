import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const read = (file) => fs.readFileSync(path.join(root, file), "utf8");

const home = read("android/app/src/main/java/com/adischat/app/ui/home/HomeScreen.kt");
const main = read("android/app/src/main/java/com/adischat/app/ui/main/MainScreen.kt");
const profile = read("android/app/src/main/java/com/adischat/app/ui/profile/ProfileScreen.kt");
const shop = read("android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt");
const sellerCenter = read("android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopSellerCenterScreen.kt");
const manifest = read("android/app/src/main/AndroidManifest.xml");

for (const [name, content] of Object.entries({ home, main, profile, shop, sellerCenter, manifest })) {
  if (/https:\/\/(www\.)?adischat\.com\/shop/.test(content)) {
    throw new Error(`${name} still references the website Shop URL`);
  }
  if (/WebView|InAppBrowser.*Shop|Shop.*InAppBrowser/i.test(content)) {
    throw new Error(`${name} appears to route marketplace content through WebView/browser code`);
  }
}

if (!main.includes("ShopRootScreen") || !main.includes("ShopRoute.Home")) {
  throw new Error("MainScreen does not route Home Shop to the native Shop screen");
}

if (!main.includes("SellerAccountRootScreen") || !main.includes("sellerAccountOpen")) {
  throw new Error("MainScreen does not route Seller Account to the native Seller Account screen");
}

for (const required of [
  "ShopRoute.Home",
  "ShopRoute.Product",
  "ShopRoute.Cart",
  "ShopRoute.Checkout",
  "ShopRoute.Orders",
  "SellerCenterTab.Dashboard",
  "SellerCenterScreen",
  "SellerCenterTab.Products",
  "SellerCenterTab.Orders",
]) {
  if (!shop.includes(required) && !sellerCenter.includes(required) && !main.includes(required) && !profile.includes(required) && !home.includes(required)) {
    throw new Error(`Native marketplace route guard missing ${required}`);
  }
}

if (!profile.includes("onOpenShopRoute")) {
  throw new Error("Profile no longer exposes native Shop route callback for account subpages");
}

if (manifest.includes('android:pathPrefix="/shop"')) {
  throw new Error("AndroidManifest still registers website Shop app links");
}

console.log("Native Shop routing guard passed");
