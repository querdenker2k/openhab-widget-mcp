package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WidgetPrompts {

    // ---------------------------------------------------------------------
    //  create_widget
    // ---------------------------------------------------------------------

    @Prompt(name = "create_widget",
            title = "Create widget",
            description = "Creates a new OpenHAB Main UI widget from a description. "
                    + "Locks size, color scheme, typography, and density up front "
                    + "and verifies the result via screenshot before presenting it.")
    PromptMessage createWidget(
            @PromptArg(name = "description",
                    description = "What should the widget show or do? "
                            + "E.g. 'card showing the car's current charge state and range'")
            String description,
            @PromptArg(name = "size_hint", required = false,
                    description = "Optional: desired size class (small/medium/large/wide/full)")
            String sizeHint,
            @PromptArg(name = "color_hint", required = false,
                    description = "Optional: color scheme (inherit/accent/semantic/brand), "
                            + "with hex codes if applicable")
            String colorHint,
            @PromptArg(name = "design_reference", required = false,
                    description = "Optional: another widget which should be used as reference for styling")
            String design_reference) {

        String body = CREATE_WIDGET_TEMPLATE
                .replace("{description}", nullSafe(description))
                .replace("{size_hint}", nullSafe(sizeHint))
                .replace("{color_hint}", nullSafe(colorHint))
                .replace("{design_reference}", nullSafe(design_reference));

        return PromptMessage.withUserRole(new TextContent(body));
    }

    // ---------------------------------------------------------------------
    //  refine_widget
    // ---------------------------------------------------------------------

    @Prompt(name = "refine_widget",
            title = "Refine widget",
            description = "Refines an existing widget. Fetches the current YAML via "
                    + "getWidget first, applies only the requested change, and "
                    + "verifies the result via screenshot.")
    PromptMessage refineWidget(
            @PromptArg(name = "widget_uid",
                    description = "UID of the existing widget, e.g. car_charging_widget")
            String widgetUid,
            @PromptArg(name = "change_request",
                    description = "What should be changed? "
                            + "E.g. 'darker background', 'larger font for the range value'")
            String changeRequest) {

        String body = REFINE_WIDGET_TEMPLATE
                .replace("{widget_uid}", nullSafe(widgetUid))
                .replace("{change_request}", nullSafe(changeRequest));

        return PromptMessage.withUserRole(new TextContent(body));
    }

    // ---------------------------------------------------------------------

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    // =====================================================================
    //  Prompt templates
    //  Kept in English on purpose — LLMs follow long structured prompts
    //  more reliably in English than in other languages.
    // =====================================================================

    private static final String CREATE_WIDGET_TEMPLATE = """
            You are helping the user build a custom widget for the OpenHAB Main UI.
            Widgets are defined in YAML using F7-based components (oh-label-card,
            oh-button, oh-toggle-item, oh-list-card, oh-knob-card, f7-card, f7-row,
            f7-col, etc.) and reference OpenHAB items by name.

            Your goal: produce a widget that matches what the user envisions on the
            FIRST visible result. The user should not have to debug your output.

            # Available MCP tools you will use

            Widget lifecycle:
            - listWidgets — see existing widgets and naming conventions
            - getWidget(uid) — fetch an existing widget definition
            - createOrUpdateWidget(filePath) — upload YAML from a file on the server
              filesystem (creates if missing, updates if exists). Takes a PATH, not
              YAML content. You must write the YAML to disk first.
            - previewWidget(uid, propsJson) — render the widget in the developer UI
              preview and return a PNG path. This is your primary feedback loop.
              propsJson can override widget props for testing.
            - createTestPageForWidget(widgetUid, ...) — embed the widget on a real
              sidebar page for a context-accurate preview. Only ONE test page per
              widget — check via listWidgets / page state before creating a second.
            - screenshotPage(uid) — screenshot the test page once it exists.

            Item discovery (use BEFORE writing YAML that references items):
            - listItems(nameFilter) — find the REAL item names in this user's
              OpenHAB. NEVER invent item names. If the user says "show battery
              state", search for matching items first.
            - getItemState(itemName) — peek at the current value, useful for picking
              sensible defaults, thresholds, or test propsJson values.
            These two tools are read-only and safe to call freely.

            Item creation (only on explicit request):
            - createItem, setItemMetadata, setItemStateDescriptionOptions
            
            Item state change:
            - getItemState - to check whats currently set.
            - sendItemCommand - update the state while sending a command.

            Persistence (only when the user asks for historical/trend data):
            - addPersistenceData

            Do NOT call deleteItem, deleteWidget, or deletePage unless the user
            clearly asked for deletion. These are destructive.

            # Workflow — follow strictly

            ## Step 1: Discover items

            If the request mentions any data ("battery", "charging state", "living
            room temperature"), call listItems with a relevant filter to find the
            actual item names. If several match, pick the obvious one or ask the
            user which. Never hardcode an item name you have not confirmed exists.
            If the user named items explicitly, skip discovery for those.

            ## Step 2: Lock the design specs

            Read the user's request. For each of the five dimensions below, either
            infer from the request or ask. Consolidate ALL open questions into ONE
            message — never one at a time.

            1. PURPOSE — what should the user understand at a glance?

            2. SIZE CLASS — pick exactly one (these are typical placements in the
               OpenHAB Main UI; actual rendered size depends on the page layout):
               - small   — ~200×200 px — single KPI or status indicator
               - medium  — ~400×200 px — KPI + label/trend or short list
               - large   — ~400×400 px — chart, longer list, composite card
               - wide    — ~800×200 px — multi-KPI strip
               - full    — ~800×400 px — rich composite

            3. COLOR SCHEME — pick exactly one:
               - inherit   — use OpenHAB theme variables
                             (var(--f7-card-bg-color), var(--f7-text-color), …).
                             Default and safest; respects light/dark mode.
               - accent    — inherit base + ONE accent color. Ask for hex.
               - semantic  — red / amber / green for status; neutral otherwise.
               - brand     — full custom palette. Ask for primary, secondary,
                             background, text (as hex). Confirm dark-mode behavior.
               - reference — file name which contains a widget definition. This 
                             should be used as reference for styling the widget
                             and what colors to use. 

            4. TYPOGRAPHY EMPHASIS — pick exactly one. Use these as concrete pixel
               targets in the YAML; do not invent in-between values:
               - data-first — hero numbers 32–48 px, labels 11–13 px, title 14 px
               - balanced   — title 16–18 px, body 14 px, meta 12 px
               - text-first — body 14–16 px, title 16 px, numbers de-emphasized

            5. DENSITY — compact (8 px padding) / comfortable (12 px) /
               spacious (16 px). Affects gaps and line-height too.

            If the user already pinned some dimensions in their request — or via
            the size_hint / color_hint arguments — treat them as locked and do NOT
            re-ask.

               size_hint:  {size_hint}
               color_hint: {color_hint}
            
            If the user provided a reference. Prefer this and just additionally use
            extra provided color_hint or size_hint arguments.

            ## Step 3: Build the YAML

            Write valid OpenHAB widget YAML:
            - Top level: uid, props (with parameters), timestamp (optional),
              component (the root component definition)
            - Use F7 / oh- components appropriate to the purpose
            - Reference items via =items.MyItem.state, =items.MyItem.displayState,
              or via props parameters of type ITEM (preferred — makes the widget
              reusable)
            - Apply the locked size, colors, font sizes, and padding consistently
            - Follow existing UID naming conventions you saw via listWidgets

            ## Step 4: Save and upload

            Write the YAML to the current working directory with filename <uid>.yaml to the filesystem (the
            MCP runs server-side and createOrUpdateWidget reads from disk). Then
            call createOrUpdateWidget with that absolute path.
            
            ## Step 5: Page
            
            Create a test page for the Widget. Call createTestPageForWidget(uid) to do this.
            The user can use this to interact with the widget without settings the items
            in the widget development page.
            Name it like the uid of the new Widget.

            ## Step 6: Preview

            Call previewWidget(uid, propsJson). Pass propsJson with realistic test
            values for any props the widget exposes (item names from Step 1,
            sensible titles, etc.). View the returned screenshot.

            Do also use screenshotPage to verify behavior in real
            page context.

            ## Step 7: Self-review the screenshot — DO NOT SKIP

            Examine the screenshot critically BEFORE responding to the user:
            - Is any text clipped, truncated, or overflowing?
            - Are all requested elements visible?
            - Does the rendered size roughly match the chosen size class?
            - Are colors consistent with the chosen scheme — no off-palette leaks?
            - Is the typographic hierarchy clearly distinct (title > body > meta)?
            - At the chosen density, does the layout breathe — or is it cramped or
              floating in empty space?
            - Do item references render real values, or do you see placeholder
              brackets / NULL / UNDEF? If so, fix item names or propsJson.
            - Do item based state changes on the widget work? Check this with using
              sendItemCommand.

            If anything is wrong: edit the YAML, re-upload, re-preview. Up to five
            automatic correction passes are expected. After that, surface the
            remaining problem to the user with a specific question — do not
            present a broken widget with caveats.

            ## Step 8: Present

            Show the final screenshot. In two or three sentences, name the design
            choices (size class, color scheme, typography emphasis, density).
            Offer two or three concrete refinement directions — e.g.
            "more compact?", "swap accent color?", "add a trend line?". Do not
            list every possible change.

            # User request

            {description}
            """;

    private static final String REFINE_WIDGET_TEMPLATE = """
            You are refining an existing OpenHAB Main UI widget. The user already
            has something working and wants a specific change — not a redesign.

            # Available MCP tools

            - getWidget(uid) — fetch the current YAML. ALWAYS call this first.
            - listItems(nameFilter), getItemState(itemName) — only if the change
              involves new items
            - createOrUpdateWidget(filePath) — re-upload after editing (takes a
              filesystem path, not YAML content)
            - previewWidget(uid, propsJson) — verify the result

            # Rules

            1. FETCH FIRST. Call getWidget({widget_uid}) to get the exact current
               state. Do not work from memory or assumptions about the YAML.

            2. PRESERVE BY DEFAULT. Anything the user did not explicitly ask to
               change stays exactly as it was: size, color scheme, font sizes,
               layout structure, component choices, props, item references.
               Resist the temptation to "improve" untouched parts.

            3. SCOPE THE CHANGE. Identify exactly which YAML nodes the change
               request affects. If ambiguous (e.g. "make it bigger" — bigger size
               class? bigger fonts? bigger numbers?), ask ONE clarifying question
               before editing. Do not guess.

            4. APPLY THE CHANGE MINIMALLY. Edit only the identified nodes. Keep
               everything else byte-identical where possible — same indentation,
               same key order, same comments. The diff should be small and
               obvious.

            5. SAVE, UPLOAD, PREVIEW. Write to /tmp/widget-{widget_uid}.yaml,
               call createOrUpdateWidget, then previewWidget. Self-review the
               screenshot with the standard checklist:
               - no clipping or overflow
               - all elements still visible
               - hierarchy intact
               - palette consistent
               - item values render real data (no NULL / UNDEF / brackets)
               Pay special attention to whether your change broke anything
               secondary (e.g. enlarging a number pushes a label out of frame).

               Up to two automatic correction passes are expected. After that,
               surface the issue to the user.

            6. PRESENT. Show the new screenshot. State in ONE sentence what you
               changed. If you had to adjust anything secondary to make the
               primary change work, call that out explicitly so the user can
               object.

            # Inputs

            Widget UID:        {widget_uid}
            Requested change:  {change_request}
            """;
}