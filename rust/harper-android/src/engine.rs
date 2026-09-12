use crate::models::LintResult;
use harper_core::Document;

#[derive(uniffi::Object)]
pub struct HarperEngine {}

#[uniffi::export]
impl HarperEngine {
    #[uniffi::constructor]
    pub fn create() -> Self {
        Self {}
    }

    pub fn version(&self) -> String {
        env!("CARGO_PKG_VERSION").to_string()
    }

    pub fn schema_version(&self) -> String {
        "1".to_string()
    }

    pub fn capabilities(&self) -> Vec<String> {
        vec!["grammar".to_string(), "spelling".to_string()]
    }

    pub fn lint(&self, text: String, _language: String) -> Vec<LintResult> {
        use harper_core::linting::Linter;
        use std::sync::Arc;

        let dict = Arc::new(harper_core::spell::FstDictionary::curated());
        let doc = Document::new_plain_english(&text, &*dict);
        let mut linter =
            harper_core::linting::LintGroup::new_curated(dict, harper_core::Dialect::American);
        let lints = linter.lint(&doc);

        lints
            .into_iter()
            .map(|lint| {
                // Convert harper-core char indices to UTF-16 code unit indices for Android
                let start_utf16: usize = text
                    .chars()
                    .take(lint.span.start)
                    .map(|c| c.len_utf16())
                    .sum();
                let end_utf16: usize = text
                    .chars()
                    .take(lint.span.end)
                    .map(|c| c.len_utf16())
                    .sum();

                LintResult {
                    start_utf16: start_utf16 as u32,
                    end_utf16: end_utf16 as u32,
                    message: lint.message,
                    suggestions: lint
                        .suggestions
                        .into_iter()
                        .map(|s| match s {
                            harper_core::linting::Suggestion::ReplaceWith(r) => {
                                r.iter().collect::<String>()
                            }
                            _ => String::new(),
                        })
                        .filter(|s| !s.is_empty())
                        .collect(),
                }
            })
            .collect()
    }
}
