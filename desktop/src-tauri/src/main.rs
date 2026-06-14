// Prevents a console/terminal window from opening alongside the app on Windows
// release builds (debug builds keep the console for logging).
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    adischat_desktop_lib::run()
}
