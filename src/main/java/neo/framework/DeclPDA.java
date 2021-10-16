package neo.framework;

import neo.framework.DeclManager.idDecl;
import neo.idlib.Lib.idException;
import neo.idlib.Text.Str.idStr;
import neo.idlib.Text.Token.idToken;
import neo.idlib.containers.idStrList;

import static neo.framework.Common.common;
import static neo.framework.DeclManager.DECL_LEXER_FLAGS;
import static neo.framework.DeclManager.declManager;
import static neo.framework.DeclManager.declType_t.*;
import static neo.idlib.Text.Lexer.*;

/**
 *
 */
public class DeclPDA {

    /*
     ===============================================================================

     idDeclPDA

     ===============================================================================
     */
    public static class idDeclEmail extends idDecl {

        private idStr date;
        private idStr from;
        private idStr image;
        private idStr subject;
        private idStr text;
        private idStr to;
        //
        //

        public idDeclEmail() {
        }


        @Override
        public String DefaultDefinition() {
            {
                return "{\n"
                        + "\t" + "{\n"
                        + "\t\t" + "to\t5Mail recipient\n"
                        + "\t\t" + "subject\t5Nothing\n"
                        + "\t\t" + "from\t5No one\n"
                        + "\t" + "}\n"
                        + "}";
            }
        }

        @Override
        public boolean Parse(String _text, int textLength) throws idException {
            idLexer src = new idLexer();

            src.LoadMemory(_text, textLength, GetFileName(), GetLineNum());
            src.SetFlags(LEXFL_NOSTRINGCONCAT | LEXFL_ALLOWPATHNAMES | LEXFL_ALLOWMULTICHARLITERALS | LEXFL_ALLOWBACKSLASHSTRINGCONCAT | LEXFL_NOFATALERRORS);
            src.SkipUntilString("{");

            text = new idStr("");
            // scan through, identifying each individual parameter
            while (true) {
                idToken token = new idToken();

                if (!src.ReadToken(token)) {
                    break;
                }

                if (token.equals("}")) {
                    break;
                }

                if (0 == token.Icmp("subject")) {
                    src.ReadToken(token);
                    subject = token;
                    continue;
                }

                if (0 == token.Icmp("to")) {
                    src.ReadToken(token);
                    to = token;
                    continue;
                }

                if (0 == token.Icmp("from")) {
                    src.ReadToken(token);
                    from = token;
                    continue;
                }

                if (0 == token.Icmp("date")) {
                    src.ReadToken(token);
                    date = token;
                    continue;
                }

                if (0 == token.Icmp("text")) {
                    src.ReadToken(token);
                    if (!token.equals("{")) {
                        src.Warning("Email decl '%s' had a parse error", GetName());
                        return false;
                    }
                    while (src.ReadToken(token) && !token.equals("}")) {
                        text.Append(token);
                    }
                    continue;
                }

                if (0 == token.Icmp("image")) {
                    src.ReadToken(token);
                    image = token;
                    continue;
                }
            }

            if (src.HadError()) {
                src.Warning("Email decl '%s' had a parse error", GetName());
                return false;
            }
            return true;
        }

        @Override
        public void FreeData() {
        }

        @Override
        public void Print() throws idException {
            common.Printf("Implement me\n");
        }

        @Override
        public void List() throws idException {
            common.Printf("Implement me\n");
        }
//

        public String GetFrom() {
            return from.toString();
        }

        public String GetBody() {
            return text.toString();
        }

        public String GetSubject() {
            return subject.toString();
        }

        public String GetDate() {
            return date.toString();
        }

        public String GetTo() {
            return to.toString();
        }

        public String GetImage() {
            return image.toString();
        }
    }

    public static class idDeclVideo extends idDecl {

        private idStr audio;//TODO:construction!?
        private idStr info;
        private idStr preview;
        private idStr video;
        private idStr videoName;
        //
        //

        public idDeclVideo() {
        }


        @Override
        public String DefaultDefinition() {
            return "{\n"
                    + "\t" + "{\n"
                    + "\t\t" + "name\t5Default Video\n"
                    + "\t" + "}\n"
                    + "}";
        }

        @Override
        public boolean Parse(String _text, int textLength) throws idException {
            idLexer src = new idLexer();

            src.LoadMemory(_text, textLength, GetFileName(), GetLineNum());
            src.SetFlags(LEXFL_NOSTRINGCONCAT | LEXFL_ALLOWPATHNAMES | LEXFL_ALLOWMULTICHARLITERALS | LEXFL_ALLOWBACKSLASHSTRINGCONCAT | LEXFL_NOFATALERRORS);
            src.SkipUntilString("{");

            // scan through, identifying each individual parameter
            while (true) {
                idToken token = new idToken();

                if (!src.ReadToken(token)) {
                    break;
                }

                if (token.equals("}")) {
                    break;
                }

                if (0 == token.Icmp("name")) {
                    src.ReadToken(token);
                    videoName = token;
                    continue;
                }

                if (0 == token.Icmp("preview")) {
                    src.ReadToken(token);
                    preview = token;
                    continue;
                }

                if (0 == token.Icmp("video")) {
                    src.ReadToken(token);
                    video = token;
                    declManager.FindMaterial(video);
                    continue;
                }

                if (0 == token.Icmp("info")) {
                    src.ReadToken(token);
                    info = token;
                    continue;
                }

                if (0 == token.Icmp("audio")) {
                    src.ReadToken(token);
                    audio = token;
                    declManager.FindSound(audio);
                    continue;
                }

            }

            if (src.HadError()) {
                src.Warning("Video decl '%s' had a parse error", GetName());
                return false;
            }
            return true;
        }

        @Override
        public void FreeData() {
        }

        @Override
        public void Print() throws idException {
            common.Printf("Implement me\n");
        }

        @Override
        public void List() throws idException {
            common.Printf("Implement me\n");
        }

        public String GetRoq() {
            return video.toString();
        }

        public String GetWave() {
            return audio.toString();
        }

        public String GetVideoName() {
            return videoName.toString();
        }

        public String GetInfo() {
            return info.toString();
        }

        public String GetPreview() {
            return preview.toString();
        }
    }

    public static class idDeclAudio extends idDecl {

        private idStr audio;
        private idStr audioName;
        private idStr info;
        private idStr preview;//TODO:construction!?
        //
        //

        public idDeclAudio() {
        }


        @Override
        public String DefaultDefinition() {
            return "{\n"
                    + "\t" + "{\n"
                    + "\t\t" + "name\t5Default Audio\n"
                    + "\t" + "}\n"
                    + "}";
        }

        @Override
        public boolean Parse(String text, int textLength) throws idException {
            idLexer src = new idLexer();

            src.LoadMemory(text, textLength, GetFileName(), GetLineNum());
            src.SetFlags(LEXFL_NOSTRINGCONCAT | LEXFL_ALLOWPATHNAMES | LEXFL_ALLOWMULTICHARLITERALS | LEXFL_ALLOWBACKSLASHSTRINGCONCAT | LEXFL_NOFATALERRORS);
            src.SkipUntilString("{");

            // scan through, identifying each individual parameter
            while (true) {
                idToken token = new idToken();

                if (!src.ReadToken(token)) {
                    break;
                }

                if (token.equals("}")) {
                    break;
                }

                if (0 == token.Icmp("name")) {
                    src.ReadToken(token);
                    audioName = token;
                    continue;
                }

                if (0 == token.Icmp("audio")) {
                    src.ReadToken(token);
                    audio = token;
                    declManager.FindSound(audio);
                    continue;
                }

                if (0 == token.Icmp("info")) {
                    src.ReadToken(token);
                    info = token;
                    continue;
                }

                if (0 == token.Icmp("preview")) {
                    src.ReadToken(token);
                    preview = token;
                    continue;
                }

            }

            if (src.HadError()) {
                src.Warning("Audio decl '%s' had a parse error", GetName());
                return false;
            }
            return true;
        }

        @Override
        public void FreeData() {
        }

        @Override
        public void Print() throws idException {
            common.Printf("Implement me\n");
        }

        @Override
        public void List() throws idException {
            common.Printf("Implement me\n");
        }

        public String GetAudioName() {
            return audioName.toString();
        }

        public String GetWave() {
            return audio.toString();
        }

        public String GetInfo() {
            return info.toString();
        }

        public String GetPreview() {
            return preview.toString();
        }
    }

    public static class idDeclPDA extends idDecl {

        private final idStrList audios;
        private final idStrList emails;
        private final idStr fullName;
        private final idStr icon;
        private final idStr id;
        private int originalEmails;
        private int originalVideos;
        private final idStr pdaName;
        private final idStr post;
        private final idStr security;
        private final idStr title;
        private final idStrList videos;
        //
        //

        public idDeclPDA() {
            videos = new idStrList();
            audios = new idStrList();
            emails = new idStrList();
            pdaName = new idStr();
            fullName = new idStr();
            icon = new idStr();
            id = new idStr();
            post = new idStr();
            title = new idStr();
            security = new idStr();
            originalEmails = originalVideos = 0;
        }


        @Override
        public String DefaultDefinition() {
            return "{\n"
                    + "\t" + "name  \"default pda\"\n"
                    + "}";
        }

        @Override
        public boolean Parse(final String text, final int textLength) throws idException {
            idLexer src = new idLexer();
            idToken token = new idToken();

            src.LoadMemory(text, textLength, GetFileName(), GetLineNum());
            src.SetFlags(DECL_LEXER_FLAGS);
            src.SkipUntilString("{");

            // scan through, identifying each individual parameter
            while (true) {

                if (!src.ReadToken(token)) {
                    break;
                }

                if (token.equals("}")) {
                    break;
                }

                if (0 == token.Icmp("name")) {
                    src.ReadToken(token);
                    pdaName.oSet(token);
                    continue;
                }

                if (0 == token.Icmp("fullname")) {
                    src.ReadToken(token);
                    fullName.oSet(token);
                    continue;
                }

                if (0 == token.Icmp("icon")) {
                    src.ReadToken(token);
                    icon.oSet(token);
                    continue;
                }

                if (0 == token.Icmp("id")) {
                    src.ReadToken(token);
                    id.oSet(token);
                    continue;
                }

                if (0 == token.Icmp("post")) {
                    src.ReadToken(token);
                    post.oSet(token);
                    continue;
                }

                if (0 == token.Icmp("title")) {
                    src.ReadToken(token);
                    title.oSet(token);
                    continue;
                }

                if (0 == token.Icmp("security")) {
                    src.ReadToken(token);
                    security.oSet(token);
                    continue;
                }

                if (0 == token.Icmp("pda_email")) {
                    src.ReadToken(token);
                    emails.add(token.toString());
                    declManager.FindType(DECL_EMAIL, token);
                    continue;
                }

                if (0 == token.Icmp("pda_audio")) {
                    src.ReadToken(token);
                    audios.add(token.toString());
                    declManager.FindType(DECL_AUDIO, token);
                    continue;
                }

                if (0 == token.Icmp("pda_video")) {
                    src.ReadToken(token);
                    videos.add(token.toString());
                    declManager.FindType(DECL_VIDEO, token);
                    continue;
                }

            }

            if (src.HadError()) {
                src.Warning("PDA decl '%s' had a parse error", GetName());
                return false;
            }

            originalVideos = videos.size();
            originalEmails = emails.size();
            return true;
        }

        @Override
        public void FreeData() {
            videos.clear();
            audios.clear();
            emails.clear();
            originalEmails = 0;
            originalVideos = 0;
        }

        @Override
        public void Print() throws idException {
            common.Printf("Implement me\n");
        }

        @Override
        public void List() throws idException {
            common.Printf("Implement me\n");
        }

        public void AddVideo(final String _name, boolean unique /*= true*/) throws idException {
            final idStr name = new idStr(_name);

            if (unique && (videos.findIndex(name) != null)) {
                return;
            }
            if (declManager.FindType(DECL_VIDEO, _name, false) == null) {
                common.Printf("Video %s not found\n", name);
                return;
            }
            videos.add(name);
        }

        public void AddAudio(final String _name, boolean unique /*= true*/) throws idException {
            final idStr name = new idStr(_name);

            if (unique && (audios.findIndex(name) != null)) {
                return;
            }
            if (declManager.FindType(DECL_AUDIO, _name, false) == null) {
                common.Printf("Audio log %s not found\n", name);
                return;
            }
            audios.add(name);
        }

        public void AddEmail(final String _name, boolean unique /*= true*/) throws idException {
            final idStr name = new idStr(_name);

            if (unique && (emails.findIndex(name) != null)) {
                return;
            }
            if (declManager.FindType(DECL_EMAIL, _name, false) == null) {
                common.Printf("Email %s not found\n", name);
                return;
            }
            emails.add(name);
        }

        public void AddEmail(final String _name) throws idException {
            AddEmail(_name, true);
        }

        public void RemoveAddedEmailsAndVideos() {
            int num = emails.size();
            if (originalEmails < num) {
                while (num != 0 && num > originalEmails) {
                    emails.removeAtIndex(--num);
                }
            }
            num = videos.size();
            if (originalVideos < num) {
                while (num != 0 && num > originalVideos) {
                    videos.removeAtIndex(--num);
                }
            }
        }

        public int GetNumVideos() {
            return videos.size();
        }

        public int GetNumAudios() {
            return audios.size();
        }

        public int GetNumEmails() {
            return emails.size();
        }

        public idDeclVideo GetVideoByIndex(int index) throws idException {
            if (index >= 0 && index < videos.size()) {
                return (idDeclVideo) (declManager.FindType(DECL_VIDEO, videos.get(index), false));
            }
            return null;
        }

        public idDeclAudio GetAudioByIndex(int index) throws idException {
            if (index >= 0 && index < audios.size()) {
                return (idDeclAudio) declManager.FindType(DECL_AUDIO, audios.get(index), false);
            }
            return null;
        }

        public idDeclEmail GetEmailByIndex(int index) throws idException {
            if (index >= 0 && index < emails.size()) {
                return (idDeclEmail) declManager.FindType(DECL_EMAIL, emails.get(index), false);
            }
            return null;
        }

        public void SetSecurity(final String sec) {
            security.oSet(sec);
        }

        public String GetPdaName() {
            return pdaName.toString();
        }

        public String GetSecurity() {
            return security.toString();
        }

        public String GetFullName() {
            return fullName.toString();
        }

        public String GetIcon() {
            return icon.toString();
        }

        public String GetPost() {
            return post.toString();
        }

        public String GetID() {
            return id.toString();
        }

        public String GetTitle() {
            return title.toString();
        }
    }

}
