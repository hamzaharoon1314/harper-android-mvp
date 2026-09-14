use crate::models::{EditOperation, HarperSuggestion};
use harper_core::linting::Suggestion;

pub fn convert_utf16_offsets(text: &str, char_start: usize, char_end: usize) -> (u32, u32) {
    let start_utf16: usize = text.chars().take(char_start).map(|c| c.len_utf16()).sum();
    let end_utf16: usize = text.chars().take(char_end).map(|c| c.len_utf16()).sum();
    (start_utf16 as u32, end_utf16 as u32)
}

pub fn map_suggestion(s: Suggestion, idx: usize) -> Option<HarperSuggestion> {
    match s {
        Suggestion::ReplaceWith(r) => {
            let replacement: String = r.into_iter().collect();
            Some(HarperSuggestion {
                suggestion_id: format!("sugg_{}", idx),
                display_text: format!("Replace with '{}'", replacement),
                operation: EditOperation::ReplaceWith { replacement },
            })
        }
        Suggestion::InsertAfter(r) => {
            let insertion: String = r.into_iter().collect();
            Some(HarperSuggestion {
                suggestion_id: format!("sugg_{}", idx),
                display_text: format!("Insert '{}'", insertion),
                operation: EditOperation::InsertAfter { insertion },
            })
        }
        Suggestion::Remove => Some(HarperSuggestion {
            suggestion_id: format!("sugg_{}", idx),
            display_text: "Remove".to_string(),
            operation: EditOperation::Remove,
        }),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::EditOperation;
    use harper_core::linting::Suggestion;

    #[test]
    fn test_map_replace_with() {
        let chars = vec!['h', 'e', 'l', 'l', 'o'];
        let suggestion = Suggestion::ReplaceWith(chars);
        let mapped = map_suggestion(suggestion, 0).unwrap();

        assert_eq!(mapped.suggestion_id, "sugg_0");
        assert_eq!(mapped.display_text, "Replace with 'hello'");
        match mapped.operation {
            EditOperation::ReplaceWith { replacement } => assert_eq!(replacement, "hello"),
            _ => panic!("Expected ReplaceWith operation"),
        }
    }

    #[test]
    fn test_map_insert_after() {
        let chars = vec![' ', 'w', 'o', 'r', 'l', 'd'];
        let suggestion = Suggestion::InsertAfter(chars);
        let mapped = map_suggestion(suggestion, 1).unwrap();

        assert_eq!(mapped.suggestion_id, "sugg_1");
        assert_eq!(mapped.display_text, "Insert ' world'");
        match mapped.operation {
            EditOperation::InsertAfter { insertion } => assert_eq!(insertion, " world"),
            _ => panic!("Expected InsertAfter operation"),
        }
    }

    #[test]
    fn test_map_remove() {
        let suggestion = Suggestion::Remove;
        let mapped = map_suggestion(suggestion, 2).unwrap();

        assert_eq!(mapped.suggestion_id, "sugg_2");
        assert_eq!(mapped.display_text, "Remove");
        match mapped.operation {
            EditOperation::Remove => (),
            _ => panic!("Expected Remove operation"),
        }
    }

    #[test]
    fn test_convert_utf16_offsets() {
        let text = "This is a \u{1F60A} test";
        let (start, end) = convert_utf16_offsets(text, 10, 12);
        assert_eq!(start, 10);
        assert_eq!(end, 13);
    }
}
