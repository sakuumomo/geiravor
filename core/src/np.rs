pub fn split_np(np: &str) -> (String, String) {
    let np = np.trim();
    match np.split_once(" - ") {
        Some((artist, title)) => (artist.trim().to_string(), title.trim().to_string()),
        None => (String::new(), np.to_string()),
    }
}
