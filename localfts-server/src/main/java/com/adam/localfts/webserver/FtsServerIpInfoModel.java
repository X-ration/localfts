package com.adam.localfts.webserver;

public class FtsServerIpInfoModel {

    private IpInfoItem[] items;

    public IpInfoItem[] getItems() {
        return items;
    }

    public void setItems(IpInfoItem[] items) {
        this.items = items;
    }

    public class IpInfoItem {
        private String displayName;
        private String name;
        private String[] addresses;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String[] getAddresses() {
            return addresses;
        }

        public void setAddresses(String[] addresses) {
            this.addresses = addresses;
        }
    }
}
